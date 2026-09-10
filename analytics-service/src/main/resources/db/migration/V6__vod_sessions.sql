-- A session imported from a Twitch VOD (source VOD): the video id it was read from. A Helix session
-- that was live-watched and later imported keeps its source and gains the vod_id.
alter table stream_sessions add column vod_id varchar(64);

create index idx_stream_sessions_vod on stream_sessions (streamer, vod_id);
