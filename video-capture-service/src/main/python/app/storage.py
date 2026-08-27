import shutil
import time
from dataclasses import dataclass
from pathlib import Path

import boto3
from botocore.client import Config
from botocore.exceptions import ClientError

from app.config import StorageConfig


@dataclass(frozen=True)
class StoredFrame:
    frame_ref: str
    content_type: str
    size_bytes: int
    latency_ms: int


class FrameStorage:
    def store(self, source_path: Path, object_key: str, content_type: str) -> StoredFrame:
        raise NotImplementedError


class S3FrameStorage(FrameStorage):
    def __init__(self, config: StorageConfig):
        self.config = config
        self.client = boto3.client(
            "s3",
            endpoint_url=config.endpoint,
            region_name=config.region,
            aws_access_key_id=config.access_key,
            aws_secret_access_key=config.secret_key,
            config=Config(signature_version="s3v4"),
        )
        self._ensure_bucket()

    def store(self, source_path: Path, object_key: str, content_type: str) -> StoredFrame:
        size = source_path.stat().st_size
        start = time.monotonic()
        self.client.upload_file(
            str(source_path),
            self.config.bucket,
            object_key,
            ExtraArgs={"ContentType": content_type},
        )
        latency_ms = int((time.monotonic() - start) * 1000)
        return StoredFrame(
            frame_ref=f"s3://{self.config.bucket}/{object_key}",
            content_type=content_type,
            size_bytes=size,
            latency_ms=latency_ms,
        )

    def _ensure_bucket(self) -> None:
        last_error: Exception | None = None
        for _ in range(30):
            try:
                self.client.head_bucket(Bucket=self.config.bucket)
                return
            except ClientError as exc:
                status = exc.response.get("ResponseMetadata", {}).get("HTTPStatusCode")
                if status == 404:
                    self.client.create_bucket(Bucket=self.config.bucket)
                    return
                last_error = exc
            except Exception as exc:
                last_error = exc
            time.sleep(1)
        if last_error:
            raise last_error


class FilesystemFrameStorage(FrameStorage):
    def __init__(self, config: StorageConfig):
        self.root = Path(config.filesystem_root)

    def store(self, source_path: Path, object_key: str, content_type: str) -> StoredFrame:
        destination = self.root / object_key
        destination.parent.mkdir(parents=True, exist_ok=True)
        start = time.monotonic()
        shutil.copyfile(source_path, destination)
        latency_ms = int((time.monotonic() - start) * 1000)
        return StoredFrame(
            frame_ref=f"file://{destination}",
            content_type=content_type,
            size_bytes=destination.stat().st_size,
            latency_ms=latency_ms,
        )


def create_storage(config: StorageConfig) -> FrameStorage:
    if config.backend == "filesystem":
        return FilesystemFrameStorage(config)
    return S3FrameStorage(config)
