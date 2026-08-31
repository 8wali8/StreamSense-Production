-- The optional session fields the chat event carries (see docs/schemas/sentiment-analysis-event.schema.json)
-- were published on the Kafka event since hardening/10 but not persisted, so history served from Postgres
-- returned them null. Nullable columns; existing rows stay as they are.
ALTER TABLE sentiment_events ADD COLUMN source VARCHAR(32);
ALTER TABLE sentiment_events ADD COLUMN channel_login VARCHAR(255);
ALTER TABLE sentiment_events ADD COLUMN stream_session_id VARCHAR(255);
ALTER TABLE sentiment_events ADD COLUMN twitch_stream_id VARCHAR(128);

CREATE INDEX idx_sentiment_events_stream_session
    ON sentiment_events (stream_session_id, chat_timestamp DESC);
