from pydantic import BaseModel


class SentimentRequest(BaseModel):
    eventId: str
    streamer: str
    user: str
    message: str
    timestamp: int


class SentimentResponse(BaseModel):
    label: str
    score: float
    modelVersion: str


class SponsorRelevanceRequest(BaseModel):
    eventId: str | None = None
    streamer: str
    text: str
    sponsor: str
    aliases: list[str] = []
    semanticTerms: list[str] = []
    minScore: float | None = None


class SponsorRelevanceResponse(BaseModel):
    sponsorRelevant: bool
    matchedSponsor: str | None
    matchedTerms: list[str]
    relevanceScore: float
    relevanceReason: str
    modelVersion: str


class SponsorRequest(BaseModel):
    frameId: str
    streamer: str
    frameRef: str
    frameSequence: int
    capturedAt: int
    source: str | None = None
    channelLogin: str | None = None
    streamSessionId: str | None = None
    twitchStreamId: str | None = None
    videoTimestampMs: int | None = None
    artifactContentType: str | None = None
    artifactSizeBytes: int | None = None


class SponsorResponse(BaseModel):
    sponsor: str
    confidence: float
    modelVersion: str
    x: float
    y: float
    width: float
    height: float


class SegmentationRequest(BaseModel):
    frameId: str | None = None
    frameRef: str


class RegionProposalResponse(BaseModel):
    label: str
    confidence: float
    x: float
    y: float
    width: float
    height: float
    source: str
    areaRatio: float


class SegmentationResponse(BaseModel):
    modelVersion: str
    frameWidth: int
    frameHeight: int
    proposals: list[RegionProposalResponse]


class TranscriptionResponse(BaseModel):
    text: str
    language: str | None
    confidence: float | None
    modelVersion: str
