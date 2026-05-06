import hashlib
import os
from dataclasses import dataclass
from io import BytesIO
from urllib.parse import unquote, urlparse

import boto3
from botocore.client import Config
from PIL import Image, UnidentifiedImageError


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


def frame_read_required() -> bool:
    return os.getenv("STREAMSENSE_SPONSOR_REQUIRE_FRAME_READ", "false").strip().lower() in {
        "1",
        "true",
        "yes",
        "on",
    }


def load_frame_artifact(frame_ref: str) -> FrameArtifact | None:
    frame_image = load_frame_image(frame_ref)
    return frame_image.artifact if frame_image else None


def load_frame_image(frame_ref: str) -> FrameImage | None:
    parsed = urlparse(frame_ref)
    if parsed.scheme == "file":
        return _decode_image(_read_file(_file_path(parsed)), frame_ref)
    if parsed.scheme == "s3":
        return _decode_image(_read_s3(parsed.netloc, parsed.path.lstrip("/")), frame_ref)
    if frame_read_required():
        raise FrameArtifactError(f"unsupported frameRef scheme for required frame read: {parsed.scheme or 'none'}")
    return None


def _read_file(path: str) -> bytes:
    try:
        with open(path, "rb") as handle:
            return handle.read()
    except OSError as exc:
        raise FrameArtifactError(f"failed to read frame file: {path}") from exc


def _file_path(parsed) -> str:
    path = unquote(parsed.path or parsed.netloc)
    if os.name == "nt" and path.startswith("/") and len(path) > 2 and path[2] == ":":
        return path[1:]
    return path


def _read_s3(bucket: str, key: str) -> bytes:
    client = boto3.client(
        "s3",
        endpoint_url=os.getenv("STREAMSENSE_FRAME_STORAGE_ENDPOINT"),
        region_name=os.getenv("STREAMSENSE_FRAME_STORAGE_REGION", "us-east-1"),
        aws_access_key_id=os.getenv("STREAMSENSE_FRAME_STORAGE_ACCESS_KEY"),
        aws_secret_access_key=os.getenv("STREAMSENSE_FRAME_STORAGE_SECRET_KEY"),
        config=Config(signature_version="s3v4"),
    )
    try:
        response = client.get_object(Bucket=bucket, Key=key)
        return response["Body"].read()
    except Exception as exc:
        raise FrameArtifactError(f"failed to read s3 frame artifact: s3://{bucket}/{key}") from exc


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
