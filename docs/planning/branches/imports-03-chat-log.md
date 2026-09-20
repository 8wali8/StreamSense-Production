# imports/03-chat-log

The owner's decision of 2026-09-20 after the baseline: the chat half of a VOD import comes from a chat log the streamer supplies, or there is none. Twitch's undocumented comments endpoint refuses automated access with an integrity check, Twitch offers no download of a recording's chat, and the only sanctioned source of a channel's chat is recording it live over IRC while measurement is on. Based on `fix/imports-per-half-resume` (#77), which it needs for per-half offsets.

## What changed

- **analytics-service keeps a chat log per recording** (`vod_chat_logs`, V9; `PUT /api/analytics/streams/{streamer}/vods/{vodId}/chat-log`, the file as the request body up to 25 MB, `fileName` and `timezone` as query parameters). `imports/ChatLogParser` reads three shapes by content: JSON (an array, or `comments`/`messages`; offsets or timestamps; the downloader shape with `commenter` and `message.fragments`), CSV or TSV with a header, and a chat client's text log (`[HH:MM:SS] user: message` under a `# Start logging at YYYY-MM-DD` header, wall-clock in the given zone). Lines outside the recording are dropped; the rest are sorted. The recordings list carries the log's summary.
- **An import replays the log, or has no chat half.** `VodImportService.importVod` sends chat-service the log's lines (from each half's own resume offset); without a log the chat half is `NONE`: not started, not failed, not holding progress, and the capture half alone carries the import to DONE. A log handed over while the capture half runs starts the chat half at once; one handed over after the import ended is replayed on Resume. The Twitch comments path is out of the import: `chat-service`'s importer is now `service/VodChatImportService`, replaying supplied lines in pages of 200 between stop checks, with ids `vod-<id>-log-<hash of offset, user, message>` (a counter for identical lines) so the same log twice lands on the same events. `TwitchVodCommentClient` keeps only what the demo replay alias uses.
- **Console.** Each recording row says "No chat log", "No chat log: video and audio only", "1,234 chat lines", or "1,234 chat lines, replayed on Resume", with an "Add chat log" (or "Replace chat log") file control that sends the file's text and the browser's time zone. A finished import with a log it has not replayed offers Resume. An import without chat reads "Imported (video and audio; no chat)". `api-client` gained a text body; the console's nginx allows 32 MB on `/api/`.
- **Docs.** `docs/contracts/sessions.md`, `CLAUDE.md`, and the PRD's decision paragraph.

## Verification

| Check | Command | Result |
|---|---|---|
| analytics-service | `mvn -pl analytics-service spotless:apply verify` in `maven:3.9-eclipse-temurin-21` | build success; 67 tests; coverage floor held |
| chat-service | `mvn -pl chat-service spotless:apply verify` | build success; 61 tests; coverage floor held |
| Frontend gate | `npm run codegen:check`, `lint`, `format:check`, `test:coverage`, `build` | all pass; 159 tests; statements 89.83 %, branches 85.31 % |

## What to check by hand

1. On the live site, on a deal page of a channel with recordings: a row reads "No chat log" with "Add chat log". Hand over a small CSV (`offset,user,message` header) and the row reads "N chat lines"; a file that is not a chat log is refused with a sentence saying so.
2. Import that recording: the chat log lines appear in the session's chat within a minute (the chat-service log reads `VOD chat import finished ... lines=N`), and the capture half runs as before.
3. Import a recording with no log: the row reads "Importing · N%" and then "Imported (video and audio; no chat)"; nothing in chat-service's log mentions the recording.
4. Hand a log over to that finished import: the row reads "N chat lines, replayed on Resume" and offers Resume; Resume replays the chat and the capture half runs from its end (an empty schedule) to DONE.

## Follow-ups

- The session report does not yet say "no chat recorded" when a session has none; the deal row does. A line on the report needs the import's chat state next to the session.
- A Chatterino-style log without a date header is read as time into the recording; a log that spans midnight without a header will misplace lines after it.
