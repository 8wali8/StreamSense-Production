import logging
import os
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Protocol

import numpy as np
from PIL import Image, ImageStat

logger = logging.getLogger(__name__)

SAM_VIT_B_CHECKPOINT_URL = "https://dl.fbaipublicfiles.com/segment_anything/sam_vit_b_01ec64.pth"


@dataclass(frozen=True)
class RegionProposal:
    label: str
    confidence: float
    x: float
    y: float
    width: float
    height: float
    source: str = "unknown"

    @property
    def area_ratio(self) -> float:
        return round(self.width * self.height, 6)

    @staticmethod
    def from_bounds(
        label: str,
        confidence: float,
        x: float,
        y: float,
        width: float,
        height: float,
        source: str = "unknown",
    ) -> "RegionProposal":
        normalized_width = _clamp(width)
        normalized_height = _clamp(height)
        normalized_x = min(_clamp(x), max(0.0, 1.0 - normalized_width))
        normalized_y = min(_clamp(y), max(0.0, 1.0 - normalized_height))
        return RegionProposal(
            label=label,
            confidence=_clamp(confidence),
            x=round(normalized_x, 6),
            y=round(normalized_y, 6),
            width=round(normalized_width, 6),
            height=round(normalized_height, 6),
            source=source,
        )


@dataclass(frozen=True)
class SegmentationConfig:
    backend: str
    model_path: str | None
    model_version: str
    confidence_threshold: float
    iou_threshold: float
    max_proposals: int
    model_input_size: int
    class_labels: tuple[str, ...]
    sam_model_type: str
    sam_checkpoint_url: str
    sam_cache_dir: str
    sam_device: str
    sam_auto_download: bool
    sam_points_per_side: int
    min_area_ratio: float

    @staticmethod
    def from_env() -> "SegmentationConfig":
        return SegmentationConfig(
            backend=os.getenv(
                "STREAMSENSE_SEGMENTATION_BACKEND",
                os.getenv("STREAMSENSE_SPONSOR_MODEL_BACKEND", ""),
            )
            .strip()
            .lower(),
            model_path=os.getenv(
                "STREAMSENSE_SAM_CHECKPOINT_PATH",
                os.getenv("STREAMSENSE_SPONSOR_MODEL_PATH", ""),
            ).strip()
            or None,
            model_version=os.getenv(
                "STREAMSENSE_SEGMENTATION_MODEL_VERSION",
                os.getenv("STREAMSENSE_SPONSOR_MODEL_VERSION", "sam-vit-b"),
            ).strip()
            or "sam-vit-b",
            confidence_threshold=_float_env(
                "STREAMSENSE_SEGMENTATION_CONFIDENCE_THRESHOLD",
                _float_env("STREAMSENSE_SPONSOR_CONFIDENCE_THRESHOLD", 0.25),
            ),
            iou_threshold=_float_env(
                "STREAMSENSE_SEGMENTATION_IOU_THRESHOLD",
                _float_env("STREAMSENSE_SPONSOR_IOU_THRESHOLD", 0.5),
            ),
            max_proposals=_int_env(
                "STREAMSENSE_SEGMENTATION_MAX_PROPOSALS",
                _int_env("STREAMSENSE_SPONSOR_MAX_PROPOSALS", 20),
            ),
            model_input_size=_int_env(
                "STREAMSENSE_SEGMENTATION_MODEL_INPUT_SIZE",
                _int_env("STREAMSENSE_SPONSOR_MODEL_INPUT_SIZE", 640),
            ),
            class_labels=_csv_env(
                "STREAMSENSE_SEGMENTATION_LABELS",
                _csv_env("STREAMSENSE_SPONSOR_MODEL_LABELS", ("segment",)),
            ),
            sam_model_type=os.getenv("STREAMSENSE_SAM_MODEL_TYPE", "vit_b").strip() or "vit_b",
            sam_checkpoint_url=os.getenv("STREAMSENSE_SAM_CHECKPOINT_URL", SAM_VIT_B_CHECKPOINT_URL).strip()
            or SAM_VIT_B_CHECKPOINT_URL,
            sam_cache_dir=os.getenv("STREAMSENSE_SAM_CACHE_DIR", "/models/sam").strip() or "/models/sam",
            sam_device=os.getenv("STREAMSENSE_SAM_DEVICE", "cpu").strip() or "cpu",
            sam_auto_download=_bool_env("STREAMSENSE_SAM_AUTO_DOWNLOAD", True),
            sam_points_per_side=_int_env("STREAMSENSE_SAM_POINTS_PER_SIDE", 16),
            min_area_ratio=_float_env("STREAMSENSE_SEGMENTATION_MIN_AREA_RATIO", 0.0005),
        )


class Segmenter(Protocol):
    def propose(self, image: Image.Image) -> list[RegionProposal]:
        pass


class EmptySegmenter:
    def propose(self, image: Image.Image) -> list[RegionProposal]:
        return []


class HeuristicSegmenter:
    def __init__(self, config: SegmentationConfig):
        self.config = config

    def propose(self, image: Image.Image) -> list[RegionProposal]:
        if self.config.max_proposals <= 0:
            return []

        grayscale = image.convert("L")
        stat = ImageStat.Stat(grayscale)
        contrast = stat.stddev[0] if stat.stddev else 0.0
        if contrast < 1.0:
            return []

        width, height = image.size
        aspect = width / height if height else 1.0
        box_width = 0.62 if aspect >= 1.0 else 0.78
        box_height = 0.62 if aspect <= 1.0 else 0.78
        confidence = max(
            self.config.confidence_threshold,
            min(0.95, 0.35 + (contrast / 255.0)),
        )
        return [
            RegionProposal.from_bounds(
                label="visual-region",
                confidence=confidence,
                x=(1.0 - box_width) / 2.0,
                y=(1.0 - box_height) / 2.0,
                width=box_width,
                height=box_height,
                source="heuristic",
            )
        ]


class SamSegmenter:
    def __init__(self, config: SegmentationConfig, mask_generator=None):
        self.config = config
        self._mask_generator = mask_generator

    def propose(self, image: Image.Image) -> list[RegionProposal]:
        mask_generator = self._get_mask_generator()
        if mask_generator is None:
            return []

        rgb_image = image.convert("RGB")
        try:
            masks = mask_generator.generate(np.asarray(rgb_image))
        except Exception as exc:
            logger.warning("SAM segmentation failed: %s", exc)
            return []

        return self._masks_to_proposals(masks, rgb_image.size)

    def _get_mask_generator(self):
        if self._mask_generator is not None:
            return self._mask_generator

        checkpoint = self._checkpoint_path()
        if checkpoint is None:
            return None

        try:
            import torch
            from segment_anything import SamAutomaticMaskGenerator, sam_model_registry

            sam = sam_model_registry[self.config.sam_model_type](checkpoint=str(checkpoint))
            sam.to(device=self.config.sam_device)
            self._mask_generator = SamAutomaticMaskGenerator(
                sam,
                points_per_side=max(1, self.config.sam_points_per_side),
                pred_iou_thresh=self.config.confidence_threshold,
            )
            if self.config.sam_device == "cuda" and not torch.cuda.is_available():
                logger.warning("SAM CUDA requested but CUDA is unavailable; model may fail on inference")
        except Exception as exc:
            logger.warning("failed to initialize SAM segmentation model: %s", exc)
            return None

        return self._mask_generator

    def _checkpoint_path(self) -> Path | None:
        if self.config.model_path:
            checkpoint = Path(self.config.model_path)
            if checkpoint.exists():
                return checkpoint
            logger.warning("SAM checkpoint path does not exist: %s", checkpoint)

        cache_path = Path(self.config.sam_cache_dir) / f"sam_{self.config.sam_model_type}.pth"
        if cache_path.exists():
            return cache_path
        if not self.config.sam_auto_download:
            logger.warning("SAM checkpoint missing and auto-download is disabled")
            return None

        try:
            cache_path.parent.mkdir(parents=True, exist_ok=True)
            logger.info("downloading SAM checkpoint url=%s path=%s", self.config.sam_checkpoint_url, cache_path)
            urllib.request.urlretrieve(self.config.sam_checkpoint_url, cache_path)
            return cache_path
        except Exception as exc:
            logger.warning("failed to download SAM checkpoint: %s", exc)
            return None

    def _masks_to_proposals(self, masks: list[dict], image_size: tuple[int, int]) -> list[RegionProposal]:
        image_width, image_height = image_size
        if image_width <= 0 or image_height <= 0:
            return []

        proposals: list[RegionProposal] = []
        for mask in masks:
            bbox = mask.get("bbox")
            if not bbox or len(bbox) != 4:
                continue
            confidence = _mask_confidence(mask)
            if confidence < self.config.confidence_threshold:
                continue

            x, y, width, height = bbox
            area_ratio = (float(width) * float(height)) / float(image_width * image_height)
            if area_ratio < self.config.min_area_ratio:
                continue

            proposals.append(
                RegionProposal.from_bounds(
                    label=self.config.class_labels[0],
                    confidence=confidence,
                    x=float(x) / image_width,
                    y=float(y) / image_height,
                    width=float(width) / image_width,
                    height=float(height) / image_height,
                    source="sam",
                )
            )

        return sorted(proposals, key=lambda proposal: proposal.confidence, reverse=True)[
            : max(0, self.config.max_proposals)
        ]


def create_segmenter(config: SegmentationConfig | None = None) -> Segmenter:
    resolved = config or SegmentationConfig.from_env()
    if resolved.backend == "heuristic":
        return HeuristicSegmenter(resolved)
    if resolved.backend in {"sam", "segment-anything", "segment_anything"}:
        return SamSegmenter(resolved)
    return EmptySegmenter()


def propose_regions(
    image: Image.Image,
    config: SegmentationConfig | None = None,
) -> list[RegionProposal]:
    segmenter = create_segmenter(config)
    proposals = segmenter.propose(image)
    max_proposals = (config or SegmentationConfig.from_env()).max_proposals
    return proposals[: max(0, max_proposals)]


def _mask_confidence(mask: dict) -> float:
    if "predicted_iou" in mask:
        return _clamp(float(mask["predicted_iou"]))
    if "stability_score" in mask:
        return _clamp(float(mask["stability_score"]))
    return 1.0


def _clamp(value: float) -> float:
    return max(0.0, min(1.0, float(value)))


def _bool_env(name: str, default: bool) -> bool:
    value = os.getenv(name)
    if value is None or not value.strip():
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}


def _float_env(name: str, default: float) -> float:
    value = os.getenv(name)
    if value is None or not value.strip():
        return default
    try:
        return float(value)
    except ValueError:
        return default


def _int_env(name: str, default: int) -> int:
    value = os.getenv(name)
    if value is None or not value.strip():
        return default
    try:
        return int(value)
    except ValueError:
        return default


def _csv_env(name: str, default: tuple[str, ...]) -> tuple[str, ...]:
    value = os.getenv(name)
    if value is None or not value.strip():
        return default
    labels = tuple(item.strip() for item in value.split(",") if item.strip())
    return labels or default
