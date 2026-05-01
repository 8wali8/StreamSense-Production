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


class TranscriptionResponse(BaseModel):
    text: str
    language: str | None
    confidence: float | None
    modelVersion: str
