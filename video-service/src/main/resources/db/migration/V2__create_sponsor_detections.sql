CREATE TABLE sponsor_detections (
    detection_event_id VARCHAR(64) PRIMARY KEY,
    source_frame_id VARCHAR(64) NOT NULL,
    streamer VARCHAR(255) NOT NULL,
    frame_ref VARCHAR(512) NOT NULL,
    frame_sequence BIGINT NOT NULL,
    captured_at BIGINT NOT NULL,
    processed_at BIGINT NOT NULL,
    sponsor VARCHAR(128) NOT NULL,
    confidence DOUBLE PRECISION NOT NULL,
    model_version VARCHAR(64) NOT NULL,
    box_x DOUBLE PRECISION NOT NULL,
    box_y DOUBLE PRECISION NOT NULL,
    box_width DOUBLE PRECISION NOT NULL,
    box_height DOUBLE PRECISION NOT NULL
);

CREATE INDEX idx_sponsor_detections_streamer_captured_at
    ON sponsor_detections (streamer, captured_at DESC);
