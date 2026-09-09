-- The sponsor relevance profile in effect per streamer, so a restart keeps the sponsor an operator
-- or a deal pointed the channel at. Terms are stored one per line.
CREATE TABLE sponsor_relevance_profiles (
    streamer VARCHAR(255) PRIMARY KEY,
    sponsor VARCHAR(255) NOT NULL,
    aliases VARCHAR(4000) NOT NULL DEFAULT '',
    semantic_terms VARCHAR(4000) NOT NULL DEFAULT '',
    min_score DOUBLE PRECISION,
    updated_at BIGINT NOT NULL
);
