CREATE TABLE sentiment_events (
    sentiment_event_id VARCHAR(64) PRIMARY KEY,
    source_event_id VARCHAR(64) NOT NULL,
    streamer VARCHAR(255) NOT NULL,
    user_name VARCHAR(255) NOT NULL,
    message VARCHAR(2000) NOT NULL,
    chat_timestamp BIGINT NOT NULL,
    processed_at BIGINT NOT NULL,
    label VARCHAR(32) NOT NULL,
    score DOUBLE PRECISION NOT NULL,
    model_version VARCHAR(64) NOT NULL
);

CREATE INDEX idx_sentiment_events_streamer_chat_timestamp
    ON sentiment_events (streamer, chat_timestamp DESC);
