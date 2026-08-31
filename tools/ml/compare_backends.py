#!/usr/bin/env python3
"""Record what ml-engine's real sentiment and relevance backends answer for a fixed set of inputs, and compare
two such recordings. Used to prove an ML dependency upgrade (torch, transformers, sentence-transformers)
does not change inference behaviour beyond floating-point noise.

    uv run --project ml-engine python tools/ml/compare_backends.py run --out before.json
    ... upgrade, relock, sync ...
    uv run --project ml-engine python tools/ml/compare_backends.py run --out after.json
    python tools/ml/compare_backends.py compare before.json after.json

`run` loads the configured models (the same settings classes the service uses, so the env overrides
STREAMSENSE_SENTIMENT_* / STREAMSENSE_RELEVANCE_* apply; the cache directories default to /models/...),
scores every message from the Twitch replay chat fixture plus a built-in set covering the three sentiment
classes and sponsor mentions, and writes one JSON file with the library versions. It exits 2 when either
backend fell back to its lexical implementation, because that would make the comparison meaningless.
`compare` prints a Markdown report: label and flag agreement, the largest score deltas, and every input
whose label or flag changed.
"""

from __future__ import annotations

import argparse
import importlib.metadata
import json
import pathlib
import sys

REPO = pathlib.Path(__file__).resolve().parents[2]
FIXTURE = REPO / "chat-service/src/main/resources/replay/redbull-testing-chat.json"
SPONSOR = "Red Bull"
ALIASES = ["redbull", "red bull racing"]
SEMANTIC_TERMS = ["energy drink", "livery", "sponsor"]
BUILTIN_MESSAGES = [
    "this stream is great and the chat energy is strong",
    "love this segment and the sponsor placement",
    "the pacing feels solid today",
    "minor lag earlier but the stream recovered well",
    "worst stream in weeks, the audio keeps cutting out",
    "I hate how often the ads interrupt the race",
    "the Red Bull can on the desk is a nice touch",
    "grabbing an energy drink before the next lap",
    "what time does the next segment start",
    "the weather in Monaco is 24 degrees",
    "absolutely terrible pit stop, they lost the race there",
    "GG everyone, incredible finish",
]


def inputs() -> list[str]:
    texts: list[str] = []
    if FIXTURE.exists():
        texts.extend(c["message"] for c in json.loads(FIXTURE.read_text(encoding="utf-8"))["comments"])
    texts.extend(BUILTIN_MESSAGES)
    return texts


def version(dist: str) -> str:
    try:
        return importlib.metadata.version(dist)
    except importlib.metadata.PackageNotFoundError:
        return "not installed"


def run(out: pathlib.Path) -> int:
    from ml_engine.relevance import SponsorRelevanceInput, create_relevance_analyzer
    from ml_engine.sentiment import create_sentiment_analyzer
    from ml_engine.settings import RelevanceSettings, SentimentSettings

    sentiment_config = SentimentSettings().to_config()
    relevance_config = RelevanceSettings().to_config()
    sentiment = create_sentiment_analyzer(sentiment_config)
    relevance = create_relevance_analyzer(relevance_config)
    texts = inputs()
    sentiment_rows = []
    relevance_rows = []
    for text in texts:
        s = sentiment.analyze(text)
        sentiment_rows.append(
            {"text": text, "label": s.label, "score": round(s.score, 6), "model_version": s.model_version}
        )
        r = relevance.analyze(
            SponsorRelevanceInput(text=text, sponsor=SPONSOR, aliases=ALIASES, semantic_terms=SEMANTIC_TERMS)
        )
        relevance_rows.append(
            {
                "text": text,
                "relevant": r.sponsor_relevant,
                "score": round(r.relevance_score, 6),
                "reason": r.relevance_reason,
                "matched_terms": r.matched_terms,
                "model_version": r.model_version,
            }
        )
    # A direct or semantic-term match is answered before the embedding model is consulted and legitimately
    # carries the direct analyzer's version; the same version with "no-direct-match" means the model failed.
    fell_back = [row for row in sentiment_rows if row["model_version"] != sentiment_config.model] + [
        row
        for row in relevance_rows
        if row["model_version"] != relevance_config.model
        and row["reason"] not in {"direct-match", "semantic-term-match"}
    ]
    embedded = sum(1 for row in relevance_rows if row["model_version"] == relevance_config.model)
    record = {
        "versions": {
            d: version(d)
            for d in (
                "torch",
                "torchvision",
                "transformers",
                "sentence-transformers",
                "safetensors",
                "huggingface-hub",
                "tokenizers",
                "numpy",
            )
        },
        "models": {
            "sentiment": sentiment_config.model,
            "relevance": relevance_config.model,
            "sponsor": SPONSOR,
            "aliases": ALIASES,
            "semantic_terms": SEMANTIC_TERMS,
        },
        "sentiment": sentiment_rows,
        "relevance": relevance_rows,
    }
    out.write_text(json.dumps(record, indent=2), encoding="utf-8")
    print(f"wrote {out} ({len(texts)} inputs, {embedded} scored by the embedding model) with", record["versions"])
    if fell_back:
        print(f"ERROR: {len(fell_back)} results came from a fallback backend; the models did not load", file=sys.stderr)
        return 2
    return 0


def compare(before_path: pathlib.Path, after_path: pathlib.Path) -> int:
    before = json.loads(before_path.read_text(encoding="utf-8"))
    after = json.loads(after_path.read_text(encoding="utf-8"))
    lines = ["# ml-engine backend comparison", ""]
    lines.append("| Library | Before | After |")
    lines.append("|---|---|---|")
    for dist in before["versions"]:
        lines.append(f"| {dist} | {before['versions'][dist]} | {after['versions'].get(dist, '?')} |")
    lines.append("")
    changed_any = False
    for section, key in (("sentiment", "label"), ("relevance", "relevant")):
        b_rows = {r["text"]: r for r in before[section]}
        a_rows = {r["text"]: r for r in after[section]}
        texts = [t for t in b_rows if t in a_rows]
        agree = sum(1 for t in texts if b_rows[t][key] == a_rows[t][key])
        deltas = sorted(((abs(b_rows[t]["score"] - a_rows[t]["score"]), t) for t in texts), reverse=True)
        max_delta = deltas[0][0] if deltas else 0.0
        mean_delta = sum(d for d, _ in deltas) / len(deltas) if deltas else 0.0
        lines.append(
            f"## {section}: {agree}/{len(texts)} {key}s agree, max |score delta| {max_delta:.4f}, mean {mean_delta:.4f}"
        )
        lines.append("")
        lines.append("| Input | Before | After | Score before | Score after |")
        lines.append("|---|---|---|---|---|")
        for _, t in deltas[:5]:
            scores = f"{b_rows[t]['score']:.4f} | {a_rows[t]['score']:.4f}"
            lines.append(f"| {t[:70]} | {b_rows[t][key]} | {a_rows[t][key]} | {scores} |")
        changed = [t for t in texts if b_rows[t][key] != a_rows[t][key]]
        if changed:
            changed_any = True
            lines.append("")
            lines.append(f"**{key} changed for {len(changed)} input(s):**")
            for t in changed:
                scores = f"{b_rows[t]['score']:.4f} -> {a_rows[t]['score']:.4f}"
                lines.append(f"- {t}: {b_rows[t][key]} -> {a_rows[t][key]} (scores {scores})")
        lines.append("")
    print("\n".join(lines))
    return 1 if changed_any else 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)
    p_run = sub.add_parser("run", help="score the inputs with the configured backends and write JSON")
    p_run.add_argument("--out", type=pathlib.Path, required=True)
    p_cmp = sub.add_parser(
        "compare", help="print a Markdown comparison of two recordings; exit 1 if a label or flag changed"
    )
    p_cmp.add_argument("before", type=pathlib.Path)
    p_cmp.add_argument("after", type=pathlib.Path)
    args = parser.parse_args()
    if args.command == "run":
        return run(args.out)
    return compare(args.before, args.after)


if __name__ == "__main__":
    sys.exit(main())
