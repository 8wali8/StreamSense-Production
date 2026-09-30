-- Per bucket and sponsor, how many sampled frames were actually examined for the logo (DETECTED or
-- NOT_DETECTED) and how many were not looked at because the deal had no logo (NO_LOGO). Together with
-- fallback_detection_count (UNAVAILABLE) they say whether a session was tracked on screen at all;
-- detection_count keeps counting every frame, whatever happened to it.
alter table sponsor_metric_buckets add column examined_detection_count bigint not null default 0;
alter table sponsor_metric_buckets add column no_logo_detection_count bigint not null default 0;
