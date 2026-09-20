-- One row per recording a channel has asked to import: what analytics-service last knew about the
-- import as a whole (state, the offset both halves have reached) and about each half (the chat
-- replay in chat-service, the frame and audio replay in video-capture-service). The services keep
-- their own status in memory and lose it on restart; this row is what the console reads, and it is
-- what a stop or a resume is decided from.
create table vod_imports (
    streamer varchar(255) not null,
    vod_id varchar(64) not null,
    session_id bigint,
    state varchar(16) not null,
    offset_seconds bigint not null default 0,
    duration_seconds bigint not null,
    chat_state varchar(16) not null,
    chat_offset_seconds bigint not null default 0,
    capture_state varchar(16) not null,
    capture_offset_seconds bigint not null default 0,
    last_error varchar(512),
    requested_at bigint not null,
    updated_at bigint not null,
    primary key (streamer, vod_id)
);

create index idx_vod_imports_state on vod_imports (state);
