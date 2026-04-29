import hashlib

SPONSORS = [
    "Nike",
    "Red Bull",
    "Razer",
    "Prime",
    "Logitech",
]


def compute_sponsor_detection(
    frame_ref: str,
    streamer: str,
    frame_sequence: int,
    frame_signature: str | None = None,
) -> tuple[str, float, float, float, float, float]:
    seed = f"{streamer}|{frame_ref}|{frame_sequence}|{frame_signature or ''}".encode("utf-8")
    digest = hashlib.sha256(seed).digest()

    sponsor = SPONSORS[digest[0] % len(SPONSORS)]
    confidence = round(0.55 + (digest[1] / 255.0) * 0.44, 3)
    width = round(0.18 + (digest[2] / 255.0) * 0.32, 3)
    height = round(0.12 + (digest[3] / 255.0) * 0.26, 3)
    x = round((digest[4] / 255.0) * max(0.0, 1.0 - width), 3)
    y = round((digest[5] / 255.0) * max(0.0, 1.0 - height), 3)

    return sponsor, confidence, x, y, width, height
