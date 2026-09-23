# logo/03-logo-matcher

The real detector: the deal's logo found in a frame, or honestly not. Branch 03 of `docs/planning/sponsor-logo-detection.md`, based on `main` after `logo/02` (#83). This is the branch the plan called out as the one whose numbers decide the product; the numbers below are on synthetic streams, and the replay and live rungs are still to come.

## What changed

**ml-engine.**
- `logo_match.py`: `LogoMatchDetector`, model version `logo-match-v1`. SIFT keypoints on the frame (searched at most 1280 px on its long side) and on each logo (upscaled to at least 256 px on its short side), Lowe's ratio test (0.75), one frame keypoint per match (several logo points matching the same frame point are one piece of evidence, not several), a similarity transform (scale, rotation, translation) fitted by RANSAC with at least 12 agreeing matches and 30% of the kept ones, a sanity check on the placement (convex, not mirrored, of real size, mostly inside the frame, at least 2% of the frame on each side), and a normalised correlation of the frame region warped back into the logo's frame against the logo itself (at least 0.45). A placement that fails a gate takes its agreeing matches with it and the rest are fitted again, up to three times, so a degenerate fit cannot hide the real placement. Every gate passed is `DETECTED` with the placement's box and a confidence of `0.5 + 0.25 × correlation + 0.25 × inlier strength` (always above analytics' 0.50 floor: the gates decide, the confidence says how strongly); anything else is `NOT_DETECTED`. Deterministic for the same frame and logo (RANSAC re-seeded per call). Logo features cached per ref (16), at most two frames matched at once (a semaphore), the rest wait.
- `settings.py`: `STREAMSENSE_SPONSOR_BACKEND` (`logo-match`, the default; `stub` is the placeholder) and every gate as `STREAMSENSE_SPONSOR_<NAME>`. `registry.py` builds the detector from them and `/ml/info` names the backend and model version. The route refuses (503, `frame artifact read failed`) a frame the store cannot read when the matcher is in use, since it cannot answer without pixels; video-service records that as `UNAVAILABLE`. `ml_sponsor_outcomes_total{backend,outcome}` counts answers.
- `opencv-python-headless` 5.0.0.93, pinned exactly; `uv.lock` regenerated.
- Tests keep the placeholder unless they ask for the matcher (`conftest.py`); `tests/logo_fixtures.py` draws a textured wordmark, a busy stream-like background, and the three placements, run through JPEG at the capture quality; `tests/test_logo_match.py` checks each placement is found with its box (IoU above 0.6), an empty frame and a different logo are `NOT_DETECTED`, the answer is the same twice, no logo refs is not a guess, a missing frame is a `FrameArtifactError`, and the endpoint runs the matcher and reports it on `/ml/info`.

**`tools/ml/eval_logo_detector.py`.** `synthesize` writes the PRD's three controlled streams as sampled frames (one every ten seconds) with a `schedule.json`; `run` detects them with the matcher (the service's own settings, so the `STREAMSENSE_SPONSOR_*` overrides apply) and prints the quality table in the PRD's shape. A real stream's frames go through the same `run` with a schedule written from the overlay plan.

**Compose and Kubernetes** name the backend (`STREAMSENSE_SPONSOR_BACKEND=logo-match`), so no deployment runs the placeholder by accident. **Docs**: `docs/contracts/sponsor-pipeline.md` (the detector and its gates), CLAUDE.md (the env toggle, the pin, the harness as the matcher's comparison procedure), this note.

## What the first version found on the way

The first fit was a homography, and one corner frame in 36 was missed although 135 matches and 66 RANSAC inliers agreed. The probe (`tools` had none; an ad hoc script) showed the fitted model had scale 0.001: many logo keypoints had matched the same frame keypoint, and a fit that collapses every logo point onto that spot counts them all as agreeing, outscoring the true placement's 50 honest agreements. Two changes fixed it and are what the branch ships: the similarity transform (an overlay is placed and scaled, never skewed; four degrees of freedom are far harder for a repeated pattern to fool than eight), and one frame keypoint per match plus a refit without a rejected placement's inliers. The synthetic logo's six near-identical letter boxes are exactly the repeated structure a real wordmark can have.

## Deliberately left alone

- **Thresholds are the defaults, not tuned to the synthetic set.** They met the bar as first written (after the two structural fixes above); the acceptance streams in `logo/07` set them from real frames.
- **Transparency.** A PNG's alpha is dropped by the frame store (RGB conversion), so a transparent logo carries whatever colour its transparent pixels held, usually black or white. Keypoints inside the logo still match; a logo whose transparent area is noisy could produce spurious keypoints. Noted for the acceptance run.
- **The replay rung** (the `redbull-testing` alias with a Red Bull logo on a deal) needs the stack up with the ML models; it is the first thing to do on the VM after this merges, before `logo/04`.
- **The ops pill and the deploy check** that refuse the placeholder are `logo/06`; this branch only makes the placeholder something one has to ask for by name.

## Verification

Run on 2026-09-23 with uv on Windows (Python 3.11 managed by uv), Docker for the image build.

| Check | Command | Result |
|---|---|---|
| ml-engine | `uv run ruff check`, `ruff format --check`, `pytest` | clean; 87 passed, 1 skipped (9 new in `test_logo_match.py`). mypy could not run on this machine (an application-control policy blocks the binary); CI runs it |
| Harness, synthetic acceptance | `eval_logo_detector.py synthesize` then `run` (180 frames: corner minutes 3 to 6; corner, lower third, full screen a minute each; no overlay) | false on-screen time 0.00 min/h (0 of 144 logo-absent frames); missed 0.0% (0 of 36); on-off-on runs 0; corner 100%, lower third 100%, full 100%; median 65 ms, max 102 ms per frame on the build machine's CPU |
| Image | `docker build ml-engine`, then `import cv2, ml_engine.logo_match` inside it | builds; cv2 5.0.0 and `logo-match-v1` import in the container |
| Compose, Kubernetes | `docker compose config -q`, `kubectl kustomize .`, `tools/k8s/check_network_policies.py` | both render; 62 edges allowed by 19 policies (no new edge) |

## What to check by hand

1. On a stack with a deal that has a logo and the replay alias running: `/ops` shows `outcome=DETECTED` with `model=logo-match-v1` only where the Red Bull logo is actually on screen, and `NOT_DETECTED` elsewhere; open a few detections' frames and confirm the box sits on the logo.
2. `curl :8000/ml/info` names `logo-match` / `logo-match-v1` for the sponsor backend.
3. `STREAMSENSE_SPONSOR_BACKEND=stub docker compose up -d ml-engine` brings the placeholder back for a demo export, and `/ml/info` says `deterministic-stub`.
4. Sample the VM's ml-engine log for `sponsor request processed`: the time between a frame's arrival and its answer stays well under the ten-second cadence with chat sentiment and transcription running beside it (the harness's 65 ms is the matcher alone).
