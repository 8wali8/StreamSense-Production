import hashlib


def compute_sentiment(message: str) -> tuple[str, float]:
    normalized = message.strip().lower()

    digest = hashlib.sha256(normalized.encode("utf-8")).hexdigest()
    raw_value = int(digest[:8], 16)

    # Map hash deterministically into [-1.0, 1.0]
    score = (raw_value / 0xFFFFFFFF) * 2.0 - 1.0
    score = round(score, 3)

    if score > 0.2:
        label = "POSITIVE"
    elif score < -0.2:
        label = "NEGATIVE"
    else:
        label = "NEUTRAL"

    return label, score
