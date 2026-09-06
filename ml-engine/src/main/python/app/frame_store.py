"""Reads frame artifacts referenced by ``file://`` or ``s3://`` URIs."""

from __future__ import annotations

import hashlib
import os
import stat
import threading
from dataclasses import dataclass
from io import BytesIO
from typing import Any, Protocol
from urllib.parse import unquote, urlparse

from PIL import Image, UnidentifiedImageError

# Default upper bound for one frame artifact; FrameStorageSettings.max_bytes overrides it at start-up.
DEFAULT_MAX_FRAME_BYTES = 32 * 1024 * 1024


class FrameArtifactError(Exception):
    pass


@dataclass(frozen=True)
class FrameArtifact:
    checksum: str
    width: int
    height: int
    size_bytes: int

    @property
    def signature(self) -> str:
        return f"{self.checksum}:{self.width}x{self.height}:{self.size_bytes}"


@dataclass(frozen=True)
class FrameImage:
    artifact: FrameArtifact
    image: Image.Image

    @property
    def signature(self) -> str:
        return self.artifact.signature


READABLE_SCHEMES = frozenset({"file", "s3"})


def readable_frame_ref(frame_ref: str) -> bool:
    """True when ``frame_ref`` names something this store can read (``file://`` or ``s3://``)."""
    return urlparse(frame_ref).scheme in READABLE_SCHEMES


class S3Settings(Protocol):
    """The subset of frame-storage settings the store needs (see ``app.settings.FrameStorageSettings``)."""

    endpoint: str | None
    region: str
    access_key: str | None
    secret_key: str | None


class FrameStore:
    """One per process. Holds a single boto3 client, created lazily behind a lock."""

    def __init__(
        self,
        s3: S3Settings | None = None,
        *,
        require_frame_read: bool = False,
        max_frame_bytes: int = DEFAULT_MAX_FRAME_BYTES,
    ) -> None:
        self._max_frame_bytes = max_frame_bytes
        self._s3 = s3
        self.require_frame_read = require_frame_read
        self._client: Any | None = None
        self._lock = threading.Lock()

    def load_frame_artifact(self, frame_ref: str) -> FrameArtifact | None:
        frame_image = self.load_frame_image(frame_ref)
        return frame_image.artifact if frame_image else None

    def load_frame_image(self, frame_ref: str, *, required: bool | None = None) -> FrameImage | None:
        """Decode the frame behind ``frame_ref``.

        Returns ``None`` for schemes this store cannot read unless the read is required (either
        by the ``required`` argument or the store's ``require_frame_read`` default), in which
        case a :class:`FrameArtifactError` is raised. Read and decode failures always raise.
        """
        must_read = self.require_frame_read if required is None else required
        parsed = urlparse(frame_ref)
        if parsed.scheme == "file":
            return _decode_image(_read_file(_file_path(parsed), self._max_frame_bytes), frame_ref)
        if parsed.scheme == "s3":
            return _decode_image(self._read_s3(parsed.netloc, parsed.path.lstrip("/")), frame_ref)
        if must_read:
            raise FrameArtifactError(f"unsupported frameRef scheme for required frame read: {parsed.scheme or 'none'}")
        return None

    def close(self) -> None:
        with self._lock:
            client = self._client
            self._client = None
        if client is not None and hasattr(client, "close"):
            client.close()

    def _read_s3(self, bucket: str, key: str) -> bytes:
        limit = self._max_frame_bytes
        try:
            response = self._s3_client().get_object(Bucket=bucket, Key=key)
            declared = response.get("ContentLength")
            if declared is not None and int(declared) > limit:
                raise FrameArtifactError(f"frame artifact exceeds {limit} bytes: s3://{bucket}/{key}")
            data = response["Body"].read(limit + 1)
            if len(data) > limit:
                raise FrameArtifactError(f"frame artifact exceeds {limit} bytes: s3://{bucket}/{key}")
            return data
        except FrameArtifactError:
            raise
        except Exception as exc:
            raise FrameArtifactError(f"failed to read s3 frame artifact: s3://{bucket}/{key}") from exc

    def _s3_client(self) -> Any:
        if self._client is not None:
            return self._client
        with self._lock:
            if self._client is None:
                if self._s3 is None:
                    raise FrameArtifactError("s3 frame storage is not configured")
                import boto3
                from botocore.client import Config

                self._client = boto3.client(
                    "s3",
                    endpoint_url=self._s3.endpoint,
                    region_name=self._s3.region,
                    aws_access_key_id=self._s3.access_key,
                    aws_secret_access_key=self._s3.secret_key,
                    config=Config(signature_version="s3v4"),
                )
        return self._client


# Convenience for callers and tests that only deal with local files.
_default_store = FrameStore()


def load_frame_artifact(frame_ref: str) -> FrameArtifact | None:
    return _default_store.load_frame_artifact(frame_ref)


def load_frame_image(frame_ref: str) -> FrameImage | None:
    return _default_store.load_frame_image(frame_ref)


def _read_file(path: str, limit: int) -> bytes:
    # A frameRef can come from a client, so only regular files of a bounded size are read: no device
    # nodes (`/dev/zero` never ends), no FIFOs, and nothing larger than a frame can be.
    try:
        info = os.stat(path)
        if not stat.S_ISREG(info.st_mode):
            raise FrameArtifactError(f"frame file is not a regular file: {path}")
        if info.st_size > limit:
            raise FrameArtifactError(f"frame file exceeds {limit} bytes: {path}")
        with open(path, "rb") as handle:
            data = handle.read(limit + 1)
    except OSError as exc:
        raise FrameArtifactError(f"failed to read frame file: {path}") from exc
    if len(data) > limit:
        raise FrameArtifactError(f"frame file exceeds {limit} bytes: {path}")
    return data


def _file_path(parsed: Any) -> str:
    path = unquote(parsed.path or parsed.netloc)
    if os.name == "nt" and path.startswith("/") and len(path) > 2 and path[2] == ":":
        return path[1:]
    return path


def _decode_image(data: bytes, frame_ref: str) -> FrameImage:
    if not data:
        raise FrameArtifactError(f"empty frame artifact: {frame_ref}")
    try:
        with Image.open(BytesIO(data)) as image:
            width, height = image.size
            image.load()
            decoded = image.convert("RGB")
    except (UnidentifiedImageError, OSError) as exc:
        raise FrameArtifactError(f"invalid frame image: {frame_ref}") from exc

    artifact = FrameArtifact(
        checksum=hashlib.sha256(data).hexdigest(),
        width=width,
        height=height,
        size_bytes=len(data),
    )
    return FrameImage(artifact=artifact, image=decoded)
