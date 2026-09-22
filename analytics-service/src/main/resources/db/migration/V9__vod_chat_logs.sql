-- The chat log a streamer supplied for a recording, normalised to lines of (offset into the recording,
-- user, message) and kept as JSON, so an import or a resume can replay it through chat-service. One
-- log per recording; a new upload replaces it. Twitch offers no download of a VOD's chat and refuses
-- automated access to its own replay, so this is the only source of chat for a stream that was not
-- measured live.
create table vod_chat_logs (
    streamer varchar(255) not null,
    vod_id varchar(64) not null,
    file_name varchar(255),
    line_count integer not null,
    first_offset_seconds double precision not null,
    last_offset_seconds double precision not null,
    content text not null,
    uploaded_at bigint not null,
    primary key (streamer, vod_id)
);
