import hashlib
import os
from dataclasses import dataclass
from io import BytesIO
from urllib.parse import urlparse

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


def frame_read_required() -> bool:
    return os.getenv("STREAMSENSE_SPONSOR_REQUIRE_FRAME_READ", "false").strip().lower() in {
        "1",
        "true",
        "yes",
        "on",
    }


def load_frame_artifact(frame_ref: str) -> FrameArtifact | None:
    parsed = urlparse(frame_ref)
    if parsed.scheme == "file":
        return _validate_image(_read_file(parsed.path), frame_ref)
    if parsed.scheme == "s3":
        return _validate_image(_read_s3(parsed.netloc, parsed.path.lstrip("/")), frame_ref)
    if frame_read_required():
        raise FrameArtifactError(f"unsupported frameRef scheme for required frame read: {parsed.scheme or 'none'}")
    return None


def _read_file(path: str) -> bytes:
    try:
        with open(path, "rb") as handle:
            return handle.read()
    except OSError as exc:
        raise FrameArtifactError(f"failed to read frame file: {path}") from exc


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


def _validate_image(data: bytes, frame_ref: str) -> FrameArtifact:
    if not data:
        raise FrameArtifactError(f"empty frame artifact: {frame_ref}")
    try:
        with Image.open(BytesIO(data)) as image:
            image.verify()
            width, height = image.size
    except (UnidentifiedImageError, OSError) as exc:
        raise FrameArtifactError(f"invalid frame image: {frame_ref}") from exc

    return FrameArtifact(
        checksum=hashlib.sha256(data).hexdigest(),
        width=width,
        height=height,
        size_bytes=len(data),
    )
