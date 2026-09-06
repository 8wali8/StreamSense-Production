import subprocess

from app.process import run_bounded


class TwitchStreamOffline(Exception):
    pass


class TwitchStreamResolutionError(Exception):
    pass


class TwitchSourceResolver:
    def __init__(self, quality: str, timeout_seconds: int, oauth_token: str | None = None):
        self.quality = quality
        self.timeout_seconds = timeout_seconds
        self.oauth_token = oauth_token

    def resolve(self, channel: str) -> str:
        return self.resolve_url(f"https://www.twitch.tv/{channel}", channel)

    def resolve_url(self, source_url: str, label: str) -> str:
        command = [
            "streamlink",
            "--stream-url",
            source_url,
            self.quality,
        ]
        if self.oauth_token:
            token = self.oauth_token.removeprefix("oauth:")
            command[1:1] = ["--twitch-api-header", f"Authorization=OAuth {token}"]

        try:
            result = run_bounded(command, self.timeout_seconds)
        except subprocess.TimeoutExpired as exc:
            raise TwitchStreamResolutionError("stream resolution timed out") from exc

        output = f"{result.stdout}\n{result.stderr}".strip()
        if result.returncode != 0:
            if _looks_offline(output):
                raise TwitchStreamOffline(f"Twitch source {label} is offline or unavailable")
            raise TwitchStreamResolutionError(_safe_error(output))

        stream_url = result.stdout.strip().splitlines()[-1].strip() if result.stdout.strip() else ""
        if not stream_url.startswith(("http://", "https://")):
            raise TwitchStreamResolutionError("streamlink did not return an HLS URL")
        return stream_url


def _looks_offline(output: str) -> bool:
    lowered = output.lower()
    return any(
        marker in lowered
        for marker in [
            "no playable streams found",
            "stream is currently offline",
            "this channel is currently offline",
        ]
    )


def _safe_error(output: str) -> str:
    if not output:
        return "streamlink failed without output"
    return output.replace("\n", " ")[-500:]
