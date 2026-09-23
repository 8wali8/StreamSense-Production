import hashlib
from dataclasses import dataclass
from typing import Protocol

from PIL import Image

from ml_engine.segmentation import RegionProposal

SPONSORS = [
    "Nike",
    "Red Bull",
    "Razer",
    "Prime",
    "Logitech",
]

OUTCOME_DETECTED = "DETECTED"
OUTCOME_NOT_DETECTED = "NOT_DETECTED"


@dataclass(frozen=True)
class SponsorDetection:
    sponsor: str
    confidence: float
    model_version: str
    x: float
    y: float
    width: float
    height: float
    outcome: str = OUTCOME_DETECTED


@dataclass(frozen=True)
class SponsorDetectionContext:
    frame_ref: str
    streamer: str
    frame_sequence: int
    frame_signature: str | None = None
    proposals: list[RegionProposal] | None = None
    # The deal's sponsor and logos, when the channel has a deal with one: what a real detector looks for,
    # and the name every detection of the frame is stamped with.
    sponsor: str | None = None
    logo_refs: tuple[str, ...] = ()
    # The decoded frame, when the store could read it; the real detector needs it, the placeholder does not.
    frame_image: Image.Image | None = None


class SponsorDetector(Protocol):
    # True for a detector that cannot answer without the frame's pixels.
    requires_frame: bool

    @property
    def model_version(self) -> str: ...

    def detect(self, context: SponsorDetectionContext) -> SponsorDetection:
        pass


class DeterministicSponsorDetector:
    """The placeholder: a box from a hash of the frame, for tests and the demo snapshot. Never production.

    It always answers DETECTED. With a sponsor in the context the detection carries that name, so the
    analytics downstream keys it under the deal; without one it picks from the fixed list as it always did.
    """

    requires_frame = False

    @property
    def model_version(self) -> str:
        return "stub-v1"

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

        sponsor = context.sponsor or SPONSORS[digest[0] % len(SPONSORS)]
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


def not_detected(sponsor: str, model_version: str) -> SponsorDetection:
    """The answer when a detector looked for the logo and did not find it: no confidence, no box."""
    return SponsorDetection(
        sponsor=sponsor,
        confidence=0.0,
        model_version=model_version,
        x=0.0,
        y=0.0,
        width=0.0,
        height=0.0,
        outcome=OUTCOME_NOT_DETECTED,
    )


def detect_sponsor(
    frame_ref: str,
    streamer: str,
    frame_sequence: int,
    frame_signature: str | None = None,
    proposals: list[RegionProposal] | None = None,
    detector: SponsorDetector | None = None,
    sponsor: str | None = None,
    logo_refs: tuple[str, ...] = (),
    frame_image: Image.Image | None = None,
) -> SponsorDetection:
    resolved = detector or DeterministicSponsorDetector()
    return resolved.detect(
        SponsorDetectionContext(
            frame_ref=frame_ref,
            streamer=streamer,
            frame_sequence=frame_sequence,
            frame_signature=frame_signature,
            proposals=proposals,
            sponsor=sponsor,
            logo_refs=logo_refs,
            frame_image=frame_image,
        )
    )


def compute_sponsor_detection(
    frame_ref: str,
    streamer: str,
    frame_sequence: int,
    frame_signature: str | None = None,
    proposal_signature: str | None = None,
) -> tuple[str, float, float, float, float, float]:
    digest = hashlib.sha256(_seed(frame_ref, streamer, frame_sequence, frame_signature, proposal_signature)).digest()

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
    return f"{streamer}|{frame_ref}|{frame_sequence}|{frame_signature or ''}|{proposal_signature or ''}".encode()


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
