ALTER TABLE sentiment_events ADD COLUMN sponsor_relevant BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE sentiment_events ADD COLUMN matched_sponsor VARCHAR(255);
ALTER TABLE sentiment_events ADD COLUMN matched_terms VARCHAR(1000);
ALTER TABLE sentiment_events ADD COLUMN relevance_score DOUBLE PRECISION NOT NULL DEFAULT 0;
ALTER TABLE sentiment_events ADD COLUMN relevance_reason VARCHAR(255);
ALTER TABLE sentiment_events ADD COLUMN relevance_version VARCHAR(128);

CREATE INDEX idx_sentiment_events_sponsor_relevance
    ON sentiment_events (streamer, sponsor_relevant, matched_sponsor, chat_timestamp DESC);

ALTER TABLE transcript_sentiment_events ADD COLUMN sponsor_relevant BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE transcript_sentiment_events ADD COLUMN matched_sponsor VARCHAR(255);
ALTER TABLE transcript_sentiment_events ADD COLUMN matched_terms VARCHAR(1000);
ALTER TABLE transcript_sentiment_events ADD COLUMN relevance_score DOUBLE PRECISION NOT NULL DEFAULT 0;
ALTER TABLE transcript_sentiment_events ADD COLUMN relevance_reason VARCHAR(255);
ALTER TABLE transcript_sentiment_events ADD COLUMN relevance_version VARCHAR(128);

CREATE INDEX idx_transcript_sentiment_events_sponsor_relevance
    ON transcript_sentiment_events (streamer, sponsor_relevant, matched_sponsor, segment_ended_at DESC);
