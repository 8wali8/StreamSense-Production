-- What happened to the frame (DETECTED, NOT_DETECTED, NO_LOGO, UNAVAILABLE; null on rows from before this
-- column, read as UNAVAILABLE when the model version is the fallback and DETECTED otherwise), and which
-- deal and logo the detection was made against.
ALTER TABLE sponsor_detections ADD COLUMN outcome VARCHAR(16);
ALTER TABLE sponsor_detections ADD COLUMN deal_id BIGINT;
ALTER TABLE sponsor_detections ADD COLUMN logo_id BIGINT;
