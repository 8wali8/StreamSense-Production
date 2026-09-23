#!/usr/bin/env python3
"""Score the sponsor logo detector the way the PRD's quality bar does.

    uv run --project ml-engine python tools/ml/eval_logo_detector.py synthesize --out /tmp/acceptance [--logo brand.png]
    uv run --project ml-engine python tools/ml/eval_logo_detector.py run --frames /tmp/acceptance

`synthesize` writes the PRD's three controlled streams as sampled frames (one every ten seconds, JPEG at the
capture quality) with a `schedule.json` saying when the logo was on and where: ten minutes with a corner
overlay for minutes 3 to 6 only; ten minutes with a corner overlay, a lower-third banner, and a full-screen
slate for one minute each; ten minutes with no overlay. Without `--logo` it draws a synthetic wordmark.
`run` detects every frame with the matcher (the same settings classes the service uses, so the
STREAMSENSE_SPONSOR_* overrides apply) and prints the quality table: false on-screen minutes per hour of
logo-absent stream, missed share of the minutes the logo was visible, on-off-on runs on a static overlay,
recall per placement, and milliseconds per frame. A real stream is scored the same way: put its sampled
frames in a directory with a `schedule.json` of the same shape, written from the overlay schedule the
streamer kept.
"""

from __future__ import annotations

import argparse
import json
import pathlib
import statistics
import sys
import time

from PIL import Image

REPO = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "ml-engine" / "tests"))

from logo_fixtures import PLACEMENTS, composite, jpeg_roundtrip, make_background, make_logo  # noqa: E402

from ml_engine.frame_store import FrameStore  # noqa: E402
from ml_engine.logo_match import LogoMatchDetector  # noqa: E402
from ml_engine.settings import SponsorSettings  # noqa: E402
from ml_engine.sponsor import SponsorDetectionContext  # noqa: E402

INTERVAL_S = 10

# The PRD's controlled streams: (name, minutes, [(placement, from minute, to minute)]).
STREAMS = [
    ("corner-3-to-6", 10, [("corner", 3, 6)]),
    ("three-placements", 10, [("corner", 2, 3), ("lower-third", 5, 6), ("full", 8, 9)]),
    ("no-overlay", 10, []),
]


def synthesize(out: pathlib.Path, logo_path: pathlib.Path | None) -> None:
    out.mkdir(parents=True, exist_ok=True)
    logo = Image.open(logo_path).convert("RGB") if logo_path else make_logo(1)
    logo_file = out / "logo.png"
    logo.save(logo_file)
    schedule: list[dict] = []
    seed = 0
    for stream, minutes, placements in STREAMS:
        for offset in range(0, minutes * 60, INTERVAL_S):
            seed += 1
            placement = next((name for name, start, end in placements if start * 60 <= offset < end * 60), None)
            background = make_background(seed)
            frame = composite(background, logo, PLACEMENTS[placement]) if placement else background
            name = f"{stream}-{offset:05d}.jpg"
            jpeg_roundtrip(frame).save(out / name, quality=85)
            schedule.append({"frame": name, "stream": stream, "offsetSeconds": offset, "placement": placement})
    (out / "schedule.json").write_text(
        json.dumps({"logo": "logo.png", "intervalSeconds": INTERVAL_S, "frames": schedule}, indent=1) + "\n",
        encoding="utf-8",
    )
    print(f"wrote {len(schedule)} frames and schedule.json to {out}")


def run(frames: pathlib.Path) -> int:
    schedule = json.loads((frames / "schedule.json").read_text(encoding="utf-8"))
    interval = schedule.get("intervalSeconds", INTERVAL_S)
    logo_ref = (frames / schedule["logo"]).resolve().as_uri()
    detector = LogoMatchDetector(SponsorSettings().to_match_config(), FrameStore())

    results = []
    for entry in schedule["frames"]:
        path = frames / entry["frame"]
        with Image.open(path) as image:
            image.load()
            frame = image.convert("RGB")
        started = time.perf_counter()
        detection = detector.detect(
            SponsorDetectionContext(
                frame_ref=path.resolve().as_uri(),
                streamer="acceptance",
                frame_sequence=len(results),
                sponsor="Sponsor",
                logo_refs=(logo_ref,),
                frame_image=frame,
            )
        )
        results.append(
            {
                **entry,
                "expected": entry.get("placement") is not None,
                "detected": detection.outcome == "DETECTED",
                "confidence": detection.confidence,
                "ms": (time.perf_counter() - started) * 1000,
            }
        )

    absent = [r for r in results if not r["expected"]]
    present = [r for r in results if r["expected"]]
    false_frames = sum(1 for r in absent if r["detected"])
    missed_frames = sum(1 for r in present if not r["detected"])
    absent_hours = len(absent) * interval / 3600
    false_minutes_per_hour = (false_frames * interval / 60) / absent_hours if absent_hours else 0.0
    missed_share = missed_frames / len(present) if present else 0.0
    flicker = 0
    by_stream: dict[str, list[dict]] = {}
    for r in results:
        by_stream.setdefault(r["stream"], []).append(r)
    for rows in by_stream.values():
        rows.sort(key=lambda r: r["offsetSeconds"])
        for a, b, c in zip(rows, rows[1:], rows[2:], strict=False):
            if (
                a["expected"]
                and b["expected"]
                and c["expected"]
                and a["detected"]
                and not b["detected"]
                and c["detected"]
            ):
                flicker += 1
    per_placement = {}
    for name in PLACEMENTS:
        rows = [r for r in present if r["placement"] == name]
        if rows:
            per_placement[name] = sum(1 for r in rows if r["detected"]) / len(rows)
    ms = [r["ms"] for r in results]

    print("| Measure | Target | Result |")
    print("|---|---|---|")
    false_cell = f"{false_minutes_per_hour:.2f} min/h ({false_frames} of {len(absent)} frames)"
    missed_cell = f"{missed_share * 100:.1f}% ({missed_frames} of {len(present)} frames)"
    print(f"| False on-screen time | under 1 min per hour of logo-absent stream | {false_cell} |")
    print(f"| Missed on-screen time | under 10% of the minutes the logo is visible | {missed_cell} |")
    print(f"| Stability | no on-off-on runs on a static overlay | {flicker} runs |")
    print(
        "| Placement coverage | corner, lower third, full screen | "
        + ", ".join(f"{name} {share * 100:.0f}%" for name, share in per_placement.items())
        + " |"
    )
    print(f"| Speed | live cadence on the VM | median {statistics.median(ms):.0f} ms, max {max(ms):.0f} ms per frame |")
    ok = (
        false_minutes_per_hour < 1
        and missed_share < 0.10
        and flicker == 0
        and all(s > 0 for s in per_placement.values())
    )
    print()
    print("quality bar:", "MET" if ok else "NOT MET")
    return 0 if ok else 1


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = parser.add_subparsers(dest="command", required=True)
    synth = commands.add_parser("synthesize", help="write the PRD's controlled streams as frames")
    synth.add_argument("--out", type=pathlib.Path, required=True)
    synth.add_argument("--logo", type=pathlib.Path, default=None, help="the logo to place; a synthetic one otherwise")
    score = commands.add_parser("run", help="detect every frame and print the quality table")
    score.add_argument("--frames", type=pathlib.Path, required=True)
    args = parser.parse_args()
    if args.command == "synthesize":
        synthesize(args.out, args.logo)
        return 0
    return run(args.frames)


if __name__ == "__main__":
    sys.exit(main())
