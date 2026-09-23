"""The real sponsor detector: the deal's logo found in a frame by local features.

A streamer's overlay is the logo file itself, rendered at some scale and position, so the frame is
searched for the logo's own keypoints: SIFT features on both, matched with Lowe's ratio test, a
similarity transform (scale, rotation, translation: an overlay is placed and scaled, never skewed, and
four degrees of freedom are far harder for a repeated pattern to fool than a homography's eight)
fitted by RANSAC over the matches, and, when enough of them agree on one placement, the frame region
that placement names is warped back into the logo's own frame and compared with the logo by
normalised correlation. A detection has to pass every gate; failing any of them is
``NOT_DETECTED``, never a guess. The gates default to the strict side, because a false detection costs
the product its credibility with a sponsor and a missed one costs the streamer ten seconds.

Everything here is deterministic for the same frame and logo (RANSAC is re-seeded per call), so a
recording imported twice gives the same numbers.
"""

from __future__ import annotations

import logging
import threading
from collections import OrderedDict
from dataclasses import dataclass

import cv2
import numpy as np
from PIL import Image

from ml_engine.frame_store import FrameArtifactError, FrameStore
from ml_engine.sponsor import (
    OUTCOME_DETECTED,
    SponsorDetection,
    SponsorDetectionContext,
    not_detected,
)

logger = logging.getLogger(__name__)

MODEL_VERSION = "logo-match-v1"
UNKNOWN_SPONSOR = "UNKNOWN"


@dataclass(frozen=True)
class LogoMatchConfig:
    model_version: str = MODEL_VERSION
    # Frames are searched at most this wide or tall; a 1080p frame shrinks by a third, which is plenty for
    # an overlay and halves the keypoint work.
    max_frame_side: int = 1280
    # A logo shorter than this on its short side is upscaled first, so a small wordmark still yields keypoints.
    min_logo_side: int = 256
    # Lowe's ratio: a match is kept when its best candidate is this much closer than the second best.
    lowe_ratio: float = 0.75
    # The placement needs at least this many agreeing matches, and this share of the kept ones.
    min_inliers: int = 12
    min_inlier_ratio: float = 0.3
    ransac_reprojection_px: float = 4.0
    # The warped frame region has to look like the logo: normalised correlation at least this.
    min_correlation: float = 0.45
    # A box narrower or shorter than this fraction of the frame is noise, not a placement.
    min_box_side: float = 0.02
    # Detections in flight at once (SIFT on a frame is CPU work); the rest wait.
    max_concurrent: int = 2
    # Logos whose features are kept in memory; a deal has at most two.
    logo_cache_size: int = 16


@dataclass(frozen=True)
class LogoFeatures:
    gray: np.ndarray
    keypoints: tuple[cv2.KeyPoint, ...]
    descriptors: np.ndarray
    width: int
    height: int


@dataclass(frozen=True)
class Match:
    confidence: float
    x: float
    y: float
    width: float
    height: float
    inliers: int
    correlation: float


class LogoMatchDetector:
    """One per process. Thread-safe: the logo cache is locked, and detections are bounded by a semaphore."""

    requires_frame = True

    def __init__(self, config: LogoMatchConfig, frame_store: FrameStore) -> None:
        self.config = config
        self._frame_store = frame_store
        self._sift = cv2.SIFT_create()
        self._matcher = cv2.BFMatcher(cv2.NORM_L2)
        self._logos: OrderedDict[str, LogoFeatures] = OrderedDict()
        self._lock = threading.Lock()
        self._slots = threading.Semaphore(max(1, config.max_concurrent))

    @property
    def model_version(self) -> str:
        return self.config.model_version

    def detect(self, context: SponsorDetectionContext) -> SponsorDetection:
        sponsor = context.sponsor or UNKNOWN_SPONSOR
        if context.frame_image is None:
            raise FrameArtifactError(f"logo matching needs the frame image: {context.frame_ref}")
        if not context.logo_refs:
            return not_detected(sponsor, self.config.model_version)
        with self._slots:
            frame = _prepare_frame(context.frame_image, self.config.max_frame_side)
            keypoints, descriptors = self._sift.detectAndCompute(frame, None)
            if descriptors is None or len(keypoints) < self.config.min_inliers:
                return not_detected(sponsor, self.config.model_version)
            best: Match | None = None
            for ref in context.logo_refs:
                logo = self._logo(ref)
                match = self._match(logo, frame, keypoints, descriptors)
                if match is not None and (best is None or match.confidence > best.confidence):
                    best = match
        if best is None:
            return not_detected(sponsor, self.config.model_version)
        return SponsorDetection(
            sponsor=sponsor,
            confidence=best.confidence,
            model_version=self.config.model_version,
            x=best.x,
            y=best.y,
            width=best.width,
            height=best.height,
            outcome=OUTCOME_DETECTED,
        )

    # ------------------------------------------------------------------ logos
    def _logo(self, ref: str) -> LogoFeatures:
        with self._lock:
            cached = self._logos.get(ref)
            if cached is not None:
                self._logos.move_to_end(ref)
                return cached
        loaded = self._frame_store.load_frame_image(ref, required=True)
        if loaded is None:
            raise FrameArtifactError(f"logo image could not be read: {ref}")
        features = self._features_of(loaded.image)
        with self._lock:
            self._logos[ref] = features
            while len(self._logos) > self.config.logo_cache_size:
                self._logos.popitem(last=False)
        logger.info("logo features ready ref=%s keypoints=%s", ref, len(features.keypoints))
        return features

    def _features_of(self, image: Image.Image) -> LogoFeatures:
        gray = cv2.cvtColor(np.asarray(image.convert("RGB")), cv2.COLOR_RGB2GRAY)
        short = min(gray.shape[:2])
        if 0 < short < self.config.min_logo_side:
            scale = self.config.min_logo_side / short
            gray = cv2.resize(gray, None, fx=scale, fy=scale, interpolation=cv2.INTER_CUBIC)
        keypoints, descriptors = self._sift.detectAndCompute(gray, None)
        if descriptors is None:
            descriptors = np.zeros((0, 128), dtype=np.float32)
        height, width = gray.shape[:2]
        return LogoFeatures(gray, tuple(keypoints), descriptors, width, height)

    # ------------------------------------------------------------------ matching
    def _match(
        self,
        logo: LogoFeatures,
        frame: np.ndarray,
        frame_keypoints: tuple[cv2.KeyPoint, ...],
        frame_descriptors: np.ndarray,
    ) -> Match | None:
        config = self.config
        if len(logo.keypoints) < config.min_inliers:
            return None
        pairs = self._matcher.knnMatch(logo.descriptors, frame_descriptors, k=2)
        good = [pair[0] for pair in pairs if len(pair) == 2 and pair[0].distance < config.lowe_ratio * pair[1].distance]
        # One frame keypoint per match: several logo points matching the same frame point are one piece of
        # evidence, not several, and a fit that collapses onto that point must not count them all as agreeing.
        by_target: dict[int, cv2.DMatch] = {}
        for match in good:
            kept = by_target.get(match.trainIdx)
            if kept is None or match.distance < kept.distance:
                by_target[match.trainIdx] = match
        remaining = sorted(by_target.values(), key=lambda m: (m.distance, m.queryIdx))
        frame_height, frame_width = frame.shape[:2]
        # A placement that fails a gate takes its agreeing matches with it and the rest are fitted again, so a
        # degenerate or repeated-pattern fit cannot hide the real placement beneath it.
        for _ in range(3):
            if len(remaining) < config.min_inliers:
                return None
            fit = self._fit(logo, frame, remaining, frame_keypoints, frame_width, frame_height)
            if fit is None:
                return None
            match, inlier_mask = fit
            if match is not None:
                return match
            remaining = [m for m, inlier in zip(remaining, inlier_mask, strict=True) if not inlier]
        return None

    def _fit(
        self,
        logo: LogoFeatures,
        frame: np.ndarray,
        matches: list[cv2.DMatch],
        frame_keypoints: tuple[cv2.KeyPoint, ...],
        frame_width: int,
        frame_height: int,
    ) -> tuple[Match | None, list[bool]] | None:
        """The best placement the matches agree on: the match when it passes every gate, else None plus the
        matches the failed placement used. None altogether when nothing can be fitted at all."""
        config = self.config
        src = np.float32([logo.keypoints[m.queryIdx].pt for m in matches]).reshape(-1, 1, 2)
        dst = np.float32([frame_keypoints[m.trainIdx].pt for m in matches]).reshape(-1, 1, 2)
        cv2.setRNGSeed(20260920)
        transform, mask = cv2.estimateAffinePartial2D(
            src, dst, method=cv2.RANSAC, ransacReprojThreshold=config.ransac_reprojection_px
        )
        if transform is None or mask is None:
            return None
        inlier_mask = [bool(flag) for flag in mask.reshape(-1)]
        inliers = sum(inlier_mask)
        if inliers < config.min_inliers or inliers / len(matches) < config.min_inlier_ratio:
            return None, inlier_mask

        corners = np.float32([[0, 0], [logo.width, 0], [logo.width, logo.height], [0, logo.height]]).reshape(-1, 1, 2)
        quad = cv2.transform(corners, transform).reshape(4, 2)
        if not _is_sane_quad(quad, frame_width, frame_height):
            return None, inlier_mask
        x0, y0 = np.clip(quad.min(axis=0), 0, [frame_width, frame_height])
        x1, y1 = np.clip(quad.max(axis=0), 0, [frame_width, frame_height])
        width = (x1 - x0) / frame_width
        height = (y1 - y0) / frame_height
        if width < config.min_box_side or height < config.min_box_side:
            return None, inlier_mask

        # The frame region the placement names, seen from the logo's own frame: it has to look like the logo.
        warped = cv2.warpAffine(frame, transform, (logo.width, logo.height), flags=cv2.WARP_INVERSE_MAP)
        correlation = float(cv2.matchTemplate(warped, logo.gray, cv2.TM_CCOEFF_NORMED)[0, 0])
        if not np.isfinite(correlation) or correlation < config.min_correlation:
            return None, inlier_mask

        # Every gate passed: the confidence says how strongly, and always clears analytics' acceptance floor.
        strength = min(1.0, inliers / (4.0 * config.min_inliers))
        confidence = round(0.5 + 0.25 * correlation + 0.25 * strength, 3)
        return (
            Match(
                confidence=confidence,
                x=round(float(x0) / frame_width, 3),
                y=round(float(y0) / frame_height, 3),
                width=round(float(width), 3),
                height=round(float(height), 3),
                inliers=inliers,
                correlation=round(correlation, 3),
            ),
            inlier_mask,
        )


def _prepare_frame(image: Image.Image, max_side: int) -> np.ndarray:
    gray = cv2.cvtColor(np.asarray(image.convert("RGB")), cv2.COLOR_RGB2GRAY)
    height, width = gray.shape[:2]
    longest = max(width, height)
    if longest > max_side:
        scale = max_side / longest
        gray = cv2.resize(
            gray, (max(1, round(width * scale)), max(1, round(height * scale))), interpolation=cv2.INTER_AREA
        )
    return gray


def _is_sane_quad(quad: np.ndarray, frame_width: int, frame_height: int) -> bool:
    """A placement is a convex, non-mirrored quadrilateral of real size, mostly inside the frame."""
    if not np.all(np.isfinite(quad)):
        return False
    x, y = quad[:, 0], quad[:, 1]
    # Shoelace: positive for the corner order (0,0) -> (w,0) -> (w,h) -> (0,h) in image coordinates.
    area = 0.5 * float(np.dot(x, np.roll(y, -1)) - np.dot(y, np.roll(x, -1)))
    if area <= 0:
        return False
    if not cv2.isContourConvex(quad.astype(np.float32).reshape(-1, 1, 2)):
        return False
    box_width = float(x.max() - x.min())
    box_height = float(y.max() - y.min())
    if box_width <= 0 or box_height <= 0:
        return False
    # Not absurdly skewed: the quad fills a fair share of its own bounding box.
    if area < 0.4 * box_width * box_height:
        return False
    # Mostly inside the frame: the bounding box's centre is, and the box is not wildly larger than the frame.
    centre_x = float(x.mean())
    centre_y = float(y.mean())
    if not (0 <= centre_x <= frame_width and 0 <= centre_y <= frame_height):
        return False
    return box_width <= 1.5 * frame_width and box_height <= 1.5 * frame_height
