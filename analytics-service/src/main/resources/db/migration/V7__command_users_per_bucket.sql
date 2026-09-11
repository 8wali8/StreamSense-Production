-- Distinct command users per minute bucket instead of one first-seen row per user forever. Live chat
-- events carry no stream session id, so their session key is the streamer and stays the same across
-- broadcasts; with a single first_seen_at a viewer who used the command in an earlier stream was never
-- counted in a later one. Per-bucket rows let a session count the users who used the command inside it.
create table chat_command_user_buckets (
    streamer varchar(255) not null,
    session_key varchar(255) not null,
    command varchar(64) not null,
    username varchar(255) not null,
    bucket_start bigint not null,
    primary key (streamer, session_key, command, username, bucket_start)
);

create index idx_chat_command_user_buckets_window on chat_command_user_buckets (streamer, command, bucket_start);

-- Keep what was known: each user's first use lands in its minute bucket.
insert into chat_command_user_buckets (streamer, session_key, command, username, bucket_start)
select streamer, session_key, command, username, first_seen_at - mod(first_seen_at, 60000)
from chat_command_users;

drop table chat_command_users;
