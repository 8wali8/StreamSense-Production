CREATE TABLE transcript_segments (
    segment_id VARCHAR(64) PRIMARY KEY,
    streamer VARCHAR(255) NOT NULL,
    text VARCHAR(4000) NOT NULL,
    started_at BIGINT NOT NULL,
    ended_at BIGINT NOT NULL,
    language VARCHAR(32),
    confidence DOUBLE PRECISION,
    model_version VARCHAR(128) NOT NULL,
    source VARCHAR(32) NOT NULL,
    channel_login VARCHAR(255),
    stream_session_id VARCHAR(255) NOT NULL,
    twitch_stream_id VARCHAR(255),
    video_timestamp_ms BIGINT NOT NULL,
    transcript_sequence BIGINT NOT NULL,
    capture_worker_id VARCHAR(255)
);

CREATE INDEX idx_transcript_segments_streamer_ended_at
    ON transcript_segments (streamer, ended_at DESC);

CREATE TABLE transcript_sentiment_events (
    sentiment_event_id VARCHAR(64) PRIMARY KEY,
    segment_id VARCHAR(64) NOT NULL,
    streamer VARCHAR(255) NOT NULL,
    text VARCHAR(4000) NOT NULL,
    segment_started_at BIGINT NOT NULL,
    segment_ended_at BIGINT NOT NULL,
    processed_at BIGINT NOT NULL,
    label VARCHAR(32) NOT NULL,
    score DOUBLE PRECISION NOT NULL,
    model_version VARCHAR(64) NOT NULL,
    transcript_model_version VARCHAR(128) NOT NULL,
    stream_session_id VARCHAR(255) NOT NULL,
    transcript_sequence BIGINT NOT NULL
);

CREATE INDEX idx_transcript_sentiment_events_streamer_segment_ended_at
    ON transcript_sentiment_events (streamer, segment_ended_at DESC);
