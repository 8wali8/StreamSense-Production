/** Pure arithmetic for the home page: what a streamer's past sessions add up to. */

export type SessionNumbers = {
  durationMs: number;
  onScreenMs: number;
  mentions: number;
  mentionSentiment: number | null;
  averageViewers: number | null;
};

export type TrackRecord = {
  sessions: number;
  /** Sponsor time on screen per sponsored hour, in ms. */
  onScreenMsPerHour: number | null;
  /** Mention-weighted average sentiment of sponsor mentions. */
  mentionSentiment: number | null;
  /** Average of the sessions' average viewers, ignoring sessions without viewer data. */
  averageViewers: number | null;
  totalMentions: number;
};

export function trackRecord(sessions: SessionNumbers[]): TrackRecord {
  const totalMs = sessions.reduce((sum, session) => sum + Math.max(0, session.durationMs), 0);
  const onScreenMs = sessions.reduce((sum, session) => sum + Math.max(0, session.onScreenMs), 0);
  const totalMentions = sessions.reduce((sum, session) => sum + session.mentions, 0);
  const weightedSentiment = sessions.reduce(
    (sum, session) => sum + (session.mentionSentiment ?? 0) * (session.mentionSentiment == null ? 0 : session.mentions),
    0,
  );
  const sentimentWeight = sessions.reduce(
    (sum, session) => sum + (session.mentionSentiment == null ? 0 : session.mentions),
    0,
  );
  const viewerSessions = sessions.filter((session) => session.averageViewers != null);
  return {
    sessions: sessions.length,
    onScreenMsPerHour: totalMs === 0 ? null : (onScreenMs / totalMs) * 3_600_000,
    mentionSentiment: sentimentWeight === 0 ? null : weightedSentiment / sentimentWeight,
    averageViewers:
      viewerSessions.length === 0
        ? null
        : viewerSessions.reduce((sum, session) => sum + (session.averageViewers ?? 0), 0) / viewerSessions.length,
    totalMentions,
  };
}
