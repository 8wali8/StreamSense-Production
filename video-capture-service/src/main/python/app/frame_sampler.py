import subprocess
import time
from pathlib import Path


class FrameCaptureError(Exception):
    pass


class FrameSampler:
    def __init__(self, timeout_seconds: int, output_format: str = "jpg", jpeg_quality: int = 85):
        self.timeout_seconds = timeout_seconds
        self.output_format = output_format
        self.jpeg_quality = jpeg_quality

    def capture(self, hls_url: str, output_path: Path, seek_seconds: float | None = None) -> tuple[Path, int]:
        output_path.parent.mkdir(parents=True, exist_ok=True)
        quality = max(2, min(31, round((100 - self.jpeg_quality) / 4) + 2))
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
        command.extend([
            "-i",
            hls_url,
            "-frames:v",
            "1",
            "-q:v",
            str(quality),
            str(output_path),
        ])

        start = time.monotonic()
        try:
            result = subprocess.run(
                command,
                capture_output=True,
                text=True,
                timeout=self.timeout_seconds,
                check=False,
            )
        except subprocess.TimeoutExpired as exc:
            raise FrameCaptureError("ffmpeg frame capture timed out") from exc

        latency_ms = int((time.monotonic() - start) * 1000)
        if result.returncode != 0:
            error = result.stderr.strip().replace("\n", " ")[-500:] or "ffmpeg failed without stderr"
            raise FrameCaptureError(error)
        if not output_path.exists() or output_path.stat().st_size == 0:
            raise FrameCaptureError("ffmpeg produced an empty frame artifact")
        return output_path, latency_ms
