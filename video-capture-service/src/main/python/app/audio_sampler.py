import subprocess
import time
from pathlib import Path

from app.process import run_bounded


class AudioCaptureError(Exception):
    pass


class AudioSampler:
    def __init__(self, timeout_seconds: int, duration_seconds: int):
        self.timeout_seconds = timeout_seconds
        self.duration_seconds = duration_seconds

    def capture(self, hls_url: str, output_path: Path, seek_seconds: float | None = None) -> tuple[Path, int]:
        output_path.parent.mkdir(parents=True, exist_ok=True)
        command = [
            "ffmpeg",
            "-y",
            "-loglevel",
            "warning",
            "-rw_timeout",
            str(self.timeout_seconds * 1_000_000),
        ]
        if seek_seconds is not None:
            command.extend(["-ss", f"{max(0.0, seek_seconds):.3f}"])
        command.extend(
            [
                "-i",
                hls_url,
                "-t",
                str(self.duration_seconds),
                "-vn",
                "-ac",
                "1",
                "-ar",
                "16000",
                "-c:a",
                "pcm_s16le",
                str(output_path),
            ]
        )

        start = time.monotonic()
        try:
            result = run_bounded(command, self.timeout_seconds)
        except subprocess.TimeoutExpired as exc:
            raise AudioCaptureError("ffmpeg audio capture timed out") from exc

        latency_ms = int((time.monotonic() - start) * 1000)
        if result.returncode != 0:
            error = result.stderr.strip().replace("\n", " ")[-500:] or "ffmpeg failed without stderr"
            raise AudioCaptureError(error)
        if not output_path.exists() or output_path.stat().st_size == 0:
            raise AudioCaptureError("ffmpeg produced an empty audio artifact")
        return output_path, latency_ms
