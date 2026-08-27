ALTER TABLE sponsor_detections ALTER COLUMN frame_ref SET DATA TYPE VARCHAR(1024);

ALTER TABLE sponsor_detections ADD COLUMN source VARCHAR(32);
ALTER TABLE sponsor_detections ADD COLUMN channel_login VARCHAR(255);
ALTER TABLE sponsor_detections ADD COLUMN stream_session_id VARCHAR(255);
ALTER TABLE sponsor_detections ADD COLUMN twitch_stream_id VARCHAR(128);
ALTER TABLE sponsor_detections ADD COLUMN video_timestamp_ms BIGINT;

CREATE INDEX idx_sponsor_detections_stream_session_captured_at
    ON sponsor_detections (stream_session_id, captured_at DESC);
