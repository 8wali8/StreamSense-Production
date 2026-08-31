# hardening/28-ml-stack-upgrade

Item 28 of `docs/planning/production-hardening-followups.md` (follow-up from branch 14): ml-engine moves from torch 2.2.2 / transformers 4.40.2 / sentence-transformers 2.6.1 to torch 2.14.0 / transformers 5.16.1 / sentence-transformers 6.0.1, the seven `.trivyignore` suppressions go away, and a checked-in comparison shows the real backends answer exactly as before. Stacked on `hardening/27-python-layout`.

## What was wrong

Branch 14's vulnerability gate found one CRITICAL in torch (CVE-2025-32434, `torch.load` with `weights_only=False`) and six HIGH in transformers, and suppressed them in `.trivyignore` because the fix versions change inference libraries and nobody had compared model outputs across the jump. The plan asked for torch ≥ 2.6 and transformers ≥ 4.48, but Trivy's fixed versions today are 2.6.0 for torch and 4.48.0, 5.3.0, 5.5.0, and 5.10.0 for the six transformers CVEs (`trivy fs --scanners vuln --ignore-unfixed` on the old `uv.lock`), so any 4.x transformers would have left three HIGH findings open.

## What changed

- **Pins** in `ml-engine/pyproject.toml` (exact, moved together, with the reasoning in a comment):

  | Library | Before | After |
  |---|---|---|
  | torch | 2.2.2+cpu | 2.14.0+cpu (PyTorch CPU index) |
  | torchvision | 0.17.2+cpu | 0.29.0+cpu (the pair for torch 2.14) |
  | transformers | 4.40.2 | 5.16.1 |
  | sentence-transformers | 2.6.1 | 6.0.1 |
  | huggingface-hub | 0.36.2 (`>=0.23,<1`) | 1.30.0 (`>=1,<2`) |
  | tokenizers | 0.19.1 | 0.23.2 |
  | numpy | 1.26.4 (`<2`) | 1.26.4 (constraint widened to `<3`; the resolver kept 1.26) |
  | safetensors, faster-whisper, segment-anything | 0.8.0, 1.2.1, 1.0 | unchanged |

  `uv.lock` regenerated (`uv lock`, 79 packages). No code change was needed: the `text-classification` pipeline with `top_k=None`, `AutoTokenizer`/`AutoModelForSequenceClassification.from_pretrained(cache_dir=…)`, and `SentenceTransformer(cache_folder=…, device=…).encode(normalize_embeddings=True)` all behave the same on the new majors.
- **`tools/ml/compare_backends.py`** (new, lint-clean under the services' ruff rules): `run --out X.json` loads the real sentiment and relevance backends through the service's own settings classes, scores the 10 messages of the Twitch replay chat fixture plus 12 built-in sentences (three sentiment classes, sponsor mentions, neutral chatter) against sponsor "Red Bull" with aliases and semantic terms, records library versions, and exits 2 if either backend fell back to its lexical path; `compare before.json after.json` prints the Markdown report and exits 1 if any label or relevance flag changed.
- **`docs/planning/branches/28-ml-stack-comparison.md`**: the report. Sentiment labels agree 22/22 and relevance flags 22/22 with a maximum score delta of 0.0000 in both (the service rounds scores to three decimals; at that precision the outputs are identical). Eleven relevance inputs went through the embedding model; the other eleven were answered by the direct sponsor match, which never touches the model.
- **`.trivyignore`** keeps only its header comment: no suppressions remain anywhere in the repository.
- **Dockerfiles (ml-engine, video-capture-service)**: the final `RUN` no longer `chown -R`s `/app`. Doing so rewrote the entire venv into the last layer; with the larger torch that took the ml-engine image to 4.97 GB. `/app` is root-owned and read-only to the service (it only imports from it; the model cache lives under `/models` and scratch under `/tmp/app`, both still owned by uid 10001). Sizes: ml-engine 2.62 GB (2.38 GB before this branch with the old stack, so the new torch adds about 0.24 GB), video-capture-service 1.08 GB (from 1.25 GB).
- **CLAUDE.md**: the ML stack is pinned exactly and moves together, and a change comes with a `compare_backends.py` report checked in next to the branch note; `.trivyignore` is a last resort, never a way to keep an old pin.

## Deliberately left alone

- The model revisions are not pinned (`cardiffnlp/twitter-roberta-base-sentiment-latest`, `sentence-transformers/all-MiniLM-L6-v2` by name); pinning a Hugging Face revision is a separate change to the settings and the cache layout.
- The plan's "torch ≥ 2.6, transformers ≥ 4.48" became 2.14 and 5.16.1 because only transformers ≥ 5.10 clears every finding; going to the current releases rather than the minimum also avoids a second upgrade branch when the next CVE lands. The transformers 5 line drops TensorFlow and Flax support, which ml-engine never used.
- Sponsor detection (`sponsor.py`, a stub backend) and transcription (faster-whisper / CTranslate2, not torch) are unaffected and not part of the comparison. Segmentation is covered by the SAM load check below rather than an output comparison, because generating masks on a real frame needs a frame fixture the repository does not carry.
- Renovate will now propose torch/transformers bumps like any other pin; the CLAUDE.md rule says to run the comparison before merging one.

## Verification

Every step ran in `python:3.11.16-slim` with uv 0.12.9, models cached in a Docker volume mounted at `/models` so both runs used the same downloaded weights.

| Check | Command | Result |
|---|---|---|
| Findings before | `trivy fs --scanners vuln --severity HIGH,CRITICAL --ignore-unfixed ml-engine/uv.lock` on the old lock (no ignore file) | 7 findings: torch CVE-2025-32434 (CRITICAL, fixed 2.6.0); transformers CVE-2024-11392/11393/11394 (fixed 4.48.0), CVE-2026-4372 (5.3.0), CVE-2026-5241 (5.5.0), CVE-2026-9856 (5.10.0) |
| Backends before | `uv sync --locked` on the old lock, `compare_backends.py run --out before.json` | 22 inputs, 11 through the embedding model, no fallback |
| Relock | `uv lock` with the new pins | resolved 79 packages; versions in the table above |
| Lint, format, types, tests on the new stack | `uv run ruff check src tests`, `ruff format --check`, `uv run mypy`, `uv run pytest` | all clean; 72 passed, 1 skipped |
| SAM under torch 2.14 | download `sam_vit_b_01ec64.pth` (375 MB), `sam_model_registry["vit_b"](checkpoint=…)`, `SamAutomaticMaskGenerator(points_per_side=4).generate()` on a synthetic 128×128 image | loads (`torch.load` now defaults to `weights_only=True`, and the checkpoint is a plain state dict); 2 masks produced |
| Backends after | `compare_backends.py run --out after.json` then `compare before.json after.json` | 22/22 sentiment labels agree, 22/22 relevance flags agree, max score delta 0.0000 in both; exit 0 |
| Vulnerability and secret gate (CI settings) | `trivy fs --scanners vuln,secret --severity HIGH,CRITICAL --ignore-unfixed --exit-code 1` with the emptied `.trivyignore` over the whole tree | exit 0 |
| Misconfiguration gate | `trivy fs --scanners misconfig --severity HIGH,CRITICAL --exit-code 1` over the tree | exit 0 |
| Images | `docker build` both services; hadolint on both Dockerfiles | build; hadolint clean; ml-engine 2.62 GB, video-capture-service 1.08 GB |
| Images run read-only | `docker run --read-only --tmpfs /tmp <image>` and `/ml/ready` / `/ready` | both ready in 4 s as uid 10001, no read-only or permission errors; the ml-engine image reports torch 2.14.0+cpu, transformers 5.16.1, sentence-transformers 6.0.1 |
| Comparison tool lint | `ruff format --line-length 120`, `ruff check --select E,W,F,I,B,UP,SIM,C4,RUF,BLE,S,N` (ruff 0.16.3) | clean |

## Manual checks for the reviewer

1. `make up` and `make smoke-e2e`: the chat → sentiment → GraphQL path still produces sentiment and relevance fields (the first request downloads the models into the `/models` volume as before).
2. `curl -s -X POST localhost:8000/ml/sentiment -H 'content-type: application/json' -d '{"eventId":"x","streamer":"s","message":"this stream is great and the chat energy is strong"}'`: label `POSITIVE`, score 0.985, matching both recordings in the comparison report.
3. `docker compose exec ml-engine python -c "import torch; print(torch.__version__)"` prints `2.14.0+cpu`; `docker images` shows ml-engine near 2.6 GB.
4. Run the tool yourself: `cd ml-engine && uv sync --locked && cd .. && uv run --project ml-engine python tools/ml/compare_backends.py run --out /tmp/now.json` (model downloads on first run), then `python tools/ml/compare_backends.py compare <after.json from the report's inputs> /tmp/now.json`.

## Follow-ups

- Pin the two Hugging Face model revisions in settings so the cache and the comparison are reproducible across model updates upstream.
- Add a frame fixture and extend the comparison to sponsor detection and segmentation once those backends are real models.
