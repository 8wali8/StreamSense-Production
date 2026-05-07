import hashlib
from dataclasses import dataclass
from typing import Protocol

from app.segmentation import RegionProposal

SPONSORS = [
    "Nike",
    "Red Bull",
    "Razer",
    "Prime",
    "Logitech",
]


@dataclass(frozen=True)
class SponsorDetection:
    sponsor: str
    confidence: float
    model_version: str
    x: float
    y: float
    width: float
    height: float


@dataclass(frozen=True)
class SponsorDetectionContext:
    frame_ref: str
    streamer: str
    frame_sequence: int
    frame_signature: str | None = None
    proposals: list[RegionProposal] | None = None


class SponsorDetector(Protocol):
    def detect(self, context: SponsorDetectionContext) -> SponsorDetection:
        pass


class DeterministicSponsorDetector:
    def detect(self, context: SponsorDetectionContext) -> SponsorDetection:
        proposals = context.proposals or []
        proposal_signature = _proposal_signature(proposals)
        seed = _seed(
            context.frame_ref,
            context.streamer,
            context.frame_sequence,
            context.frame_signature,
            proposal_signature,
        )
        digest = hashlib.sha256(seed).digest()

        sponsor = SPONSORS[digest[0] % len(SPONSORS)]
        confidence = round(0.55 + (digest[1] / 255.0) * 0.44, 3)
        top_proposal = _top_proposal(proposals)
        if top_proposal:
            confidence = round(max(confidence, top_proposal.confidence), 3)
            x = round(top_proposal.x, 3)
            y = round(top_proposal.y, 3)
            width = round(top_proposal.width, 3)
            height = round(top_proposal.height, 3)
            model_version = "proposal-aware-stub-v1"
        else:
            x, y, width, height = _synthetic_box(digest)
            model_version = "frame-aware-stub-v1" if context.frame_signature else "stub-v1"

        return SponsorDetection(
            sponsor=sponsor,
            confidence=confidence,
            model_version=model_version,
            x=x,
            y=y,
            width=width,
            height=height,
        )


def detect_sponsor(
    frame_ref: str,
    streamer: str,
    frame_sequence: int,
    frame_signature: str | None = None,
    proposals: list[RegionProposal] | None = None,
    detector: SponsorDetector | None = None,
) -> SponsorDetection:
    resolved = detector or DeterministicSponsorDetector()
    return resolved.detect(
        SponsorDetectionContext(
            frame_ref=frame_ref,
            streamer=streamer,
            frame_sequence=frame_sequence,
            frame_signature=frame_signature,
            proposals=proposals,
        )
    )


def compute_sponsor_detection(
    frame_ref: str,
    streamer: str,
    frame_sequence: int,
    frame_signature: str | None = None,
    proposal_signature: str | None = None,
) -> tuple[str, float, float, float, float, float]:
    digest = hashlib.sha256(
        _seed(frame_ref, streamer, frame_sequence, frame_signature, proposal_signature)
    ).digest()

    sponsor = SPONSORS[digest[0] % len(SPONSORS)]
    confidence = round(0.55 + (digest[1] / 255.0) * 0.44, 3)
    x, y, width, height = _synthetic_box(digest)

    return sponsor, confidence, x, y, width, height


def _seed(
    frame_ref: str,
    streamer: str,
    frame_sequence: int,
    frame_signature: str | None,
    proposal_signature: str | None,
) -> bytes:
    return f"{streamer}|{frame_ref}|{frame_sequence}|{frame_signature or ''}|{proposal_signature or ''}".encode(
        "utf-8"
    )


def _synthetic_box(digest: bytes) -> tuple[float, float, float, float]:
    width = round(0.18 + (digest[2] / 255.0) * 0.32, 3)
    height = round(0.12 + (digest[3] / 255.0) * 0.26, 3)
    x = round((digest[4] / 255.0) * max(0.0, 1.0 - width), 3)
    y = round((digest[5] / 255.0) * max(0.0, 1.0 - height), 3)
    return x, y, width, height


def _proposal_signature(proposals: list[RegionProposal]) -> str | None:
    if not proposals:
        return None
    return "|".join(
        f"{proposal.label}:{proposal.confidence:.3f}:{proposal.x:.3f}:{proposal.y:.3f}:"
        f"{proposal.width:.3f}:{proposal.height:.3f}:{proposal.source}"
        for proposal in proposals
    )


def _top_proposal(proposals: list[RegionProposal]) -> RegionProposal | None:
    if not proposals:
        return None
    return max(proposals, key=lambda proposal: proposal.confidence)
