from prometheus_client import Counter, Gauge, Histogram


capture_enabled = Gauge(
    "streamsense_twitch_video_capture_enabled",
    "Whether Twitch video capture is enabled",
)

capture_state = Gauge(
    "streamsense_twitch_video_capture_state",
    "Twitch video capture channel state as a labeled gauge",
    ["channel", "state"],
)

frames_captured = Counter(
    "streamsense_twitch_video_frames_captured_total",
    "Total frames captured from Twitch video",
    ["channel"],
)

frames_stored = Counter(
    "streamsense_twitch_video_frames_stored_total",
    "Total captured frames stored as artifacts",
    ["channel"],
)

frames_published = Counter(
    "streamsense_twitch_video_frames_published_total",
    "Total captured frame events published to Kafka",
    ["channel"],
)

frames_skipped = Counter(
    "streamsense_twitch_video_frames_skipped_total",
    "Total skipped Twitch video frames",
    ["channel", "reason"],
)

capture_errors = Counter(
    "streamsense_twitch_video_capture_errors_total",
    "Total Twitch video capture errors",
    ["channel", "stage"],
)

storage_errors = Counter(
    "streamsense_twitch_video_storage_errors_total",
    "Total Twitch video frame storage errors",
    ["channel"],
)

kafka_publish_errors = Counter(
    "streamsense_twitch_video_kafka_publish_errors_total",
    "Total Twitch video frame Kafka publish errors",
    ["channel"],
)

reconnects = Counter(
    "streamsense_twitch_video_reconnects_total",
    "Total Twitch video resolver reconnect attempts",
    ["channel"],
)

last_frame_age_seconds = Gauge(
    "streamsense_twitch_video_last_frame_age_seconds",
    "Age of the latest captured Twitch video frame",
    ["channel"],
)

capture_latency = Histogram(
    "streamsense_twitch_video_capture_latency_ms",
    "Latency of Twitch video frame capture",
    ["channel"],
)

storage_latency = Histogram(
    "streamsense_twitch_video_storage_latency_ms",
    "Latency of captured frame storage",
    ["channel"],
)

publish_latency = Histogram(
    "streamsense_twitch_video_publish_latency_ms",
    "Latency of captured frame Kafka publishing",
    ["channel"],
)

transcript_audio_captured = Counter(
    "streamsense_twitch_transcript_audio_captured_total",
    "Total audio segments captured from Twitch video",
    ["channel"],
)

transcript_segments_published = Counter(
    "streamsense_twitch_transcript_segments_published_total",
    "Total transcript segment events published to Kafka",
    ["channel"],
)

transcript_segments_skipped = Counter(
    "streamsense_twitch_transcript_segments_skipped_total",
    "Total skipped Twitch transcript segments",
    ["channel", "reason"],
)

transcript_errors = Counter(
    "streamsense_twitch_transcript_errors_total",
    "Total Twitch transcript capture or transcription errors",
    ["channel", "stage"],
)

transcript_audio_capture_latency = Histogram(
    "streamsense_twitch_transcript_audio_capture_latency_ms",
    "Latency of Twitch audio segment capture",
    ["channel"],
)

transcription_request_latency = Histogram(
    "streamsense_twitch_transcription_request_latency_ms",
    "Latency of ml-engine transcription requests",
    ["channel"],
)
