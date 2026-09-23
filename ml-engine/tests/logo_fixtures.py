"""Synthetic frames for the matcher's tests: a logo with texture, a busy stream-like background, and the
three placements the PRD names, run through JPEG the way a captured frame is."""

from __future__ import annotations

from dataclasses import dataclass
from io import BytesIO

import numpy as np
from PIL import Image, ImageDraw

FRAME_SIZE = (1280, 720)


@dataclass(frozen=True)
class Placement:
    name: str
    x: float
    y: float
    width: float
    height: float


def make_logo(seed: int, size: tuple[int, int] = (320, 160)) -> Image.Image:
    """A wordmark-like image: a white plate with a few coloured shapes and lettering, textured enough for keypoints."""
    rng = np.random.default_rng(seed)
    image = Image.new("RGB", size, "white")
    draw = ImageDraw.Draw(image)
    width, height = size
    palette = [(220, 30, 40), (20, 60, 200), (250, 190, 0), (10, 10, 10), (0, 140, 90)]
    for _ in range(6):
        colour = palette[int(rng.integers(len(palette)))]
        x0, y0 = rng.integers(0, width - 40), rng.integers(0, height - 30)
        x1, y1 = x0 + rng.integers(30, 120), y0 + rng.integers(20, 70)
        if rng.random() < 0.5:
            draw.ellipse([x0, y0, x1, y1], fill=colour)
        else:
            draw.polygon([(x0, y0), (x1, y0 + 10), (x1 - 15, y1), (x0 + 5, y1 - 5)], fill=colour)
    letters = "".join(chr(65 + int(c)) for c in rng.integers(0, 26, size=6))
    for i, letter in enumerate(letters):
        x = 14 + i * (width - 28) // 6
        draw.rectangle([x, height - 46, x + 34, height - 12], outline=(0, 0, 0), width=3)
        draw.text((x + 8, height - 42), letter, fill=(0, 0, 0))
    draw.rectangle([2, 2, width - 3, height - 3], outline=(0, 0, 0), width=4)
    return image


def make_background(seed: int, size: tuple[int, int] = FRAME_SIZE) -> Image.Image:
    """A frame with a gradient sky, blocks and circles like a game scene, and a little noise."""
    rng = np.random.default_rng(seed)
    width, height = size
    y = np.linspace(0, 1, height, dtype=np.float32)[:, None]
    base = np.stack(
        [40 + 120 * y, 60 + 90 * (1 - y), 90 + 100 * y],
        axis=-1,
    )
    base = np.repeat(base, width, axis=1)
    noise = rng.normal(0, 6, size=(height, width, 3))
    image = Image.fromarray(np.clip(base + noise, 0, 255).astype(np.uint8), "RGB")
    draw = ImageDraw.Draw(image)
    for _ in range(40):
        colour = tuple(int(c) for c in rng.integers(0, 255, size=3))
        x0, y0 = rng.integers(0, width - 60), rng.integers(0, height - 60)
        x1, y1 = x0 + rng.integers(20, 200), y0 + rng.integers(20, 160)
        if rng.random() < 0.5:
            draw.rectangle([x0, y0, x1, y1], fill=colour)
        else:
            draw.ellipse([x0, y0, x1, y1], fill=colour)
    return image


PLACEMENTS = {
    "corner": Placement("corner", 0.83, 0.03, 0.14, 0.14 * 0.5 * FRAME_SIZE[0] / FRAME_SIZE[1]),
    "lower-third": Placement("lower-third", 0.3, 0.72, 0.4, 0.4 * 0.5 * FRAME_SIZE[0] / FRAME_SIZE[1]),
    "full": Placement("full", 0.05, 0.05, 0.9, 0.9 * 0.5 * FRAME_SIZE[0] / FRAME_SIZE[1]),
}


def composite(background: Image.Image, logo: Image.Image, placement: Placement) -> Image.Image:
    """The logo pasted at a placement, its box in fractions of the frame; the logo keeps its aspect ratio."""
    frame = background.copy()
    frame_width, frame_height = frame.size
    target_width = int(placement.width * frame_width)
    target_height = int(target_width * logo.height / logo.width)
    resized = logo.resize((target_width, target_height), Image.Resampling.LANCZOS)
    frame.paste(resized, (int(placement.x * frame_width), int(placement.y * frame_height)))
    return frame


def jpeg_roundtrip(image: Image.Image, quality: int = 85) -> Image.Image:
    """What capture does to a frame: JPEG at the capture quality."""
    buffer = BytesIO()
    image.save(buffer, format="JPEG", quality=quality)
    buffer.seek(0)
    with Image.open(buffer) as decoded:
        decoded.load()
        return decoded.convert("RGB")


def iou(a: tuple[float, float, float, float], b: tuple[float, float, float, float]) -> float:
    ax0, ay0, aw, ah = a
    bx0, by0, bw, bh = b
    ix0, iy0 = max(ax0, bx0), max(ay0, by0)
    ix1, iy1 = min(ax0 + aw, bx0 + bw), min(ay0 + ah, by0 + bh)
    inter = max(0.0, ix1 - ix0) * max(0.0, iy1 - iy0)
    union = aw * ah + bw * bh - inter
    return inter / union if union > 0 else 0.0
