"""Stands in for ffmpeg in the sequential-pass tests: writes numbered frame and audio files into a
directory with a delay between them, then exits, or idles, as the arguments say.

    fake_ffmpeg_pass.py <dir> <frames> <delay_seconds> [idle]

With ``idle`` it writes nothing and sleeps, so the caller's idle timeout can be exercised.
"""

import sys
import time
from pathlib import Path


def main() -> int:
    target = Path(sys.argv[1])
    frames = int(sys.argv[2])
    delay = float(sys.argv[3])
    idle = len(sys.argv) > 4 and sys.argv[4] == "idle"
    target.mkdir(parents=True, exist_ok=True)
    if idle:
        time.sleep(60)
        return 0
    for index in range(frames):
        (target / f"f-{index:08d}.jpg").write_bytes(b"frame")
        (target / f"a-{index:08d}.wav").write_bytes(b"audio")
        time.sleep(delay)
    return 0


if __name__ == "__main__":
    sys.exit(main())
