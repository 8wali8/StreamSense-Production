/** Pure layout for the report timeline: moments become percentages along one time axis. */

import { formatOffset } from "./report-format";

export type TimelineMoment = {
  id: string;
  kind: "segment" | "voice" | "chat" | "risk";
  /** Left edge as a percentage of the stream. */
  left: number;
  /** Width as a percentage; zero for point moments. */
  width: number;
  offsetMs: number;
  title: string;
  detail: string;
  /** The vod link target or null. */
  tone: "brand" | "positive" | "negative" | "neutral";
};

export type TimelineTick = { left: number; label: string };

type SegmentLike = {
  offsetMs: number;
  durationMs: number;
  detections: number;
  peakConfidence: number;
  sponsor: string;
};
type VoiceLike = { sentimentEventId: string; offsetMs: number; label: string; score: number; text: string };
type ChatLike = {
  offsetMs: number;
  count: number;
  positiveShare: number;
  negativeShare: number;
  sample?: string | null;
};
type RiskLike = { offsetMs: number; chatNegativeRatio?: number | null; chatMessageCount: number };

export type TimelineInput = {
  durationMs: number;
  segments: SegmentLike[];
  voiceMentions: VoiceLike[];
  chatMoments: ChatLike[];
  riskSpikes: RiskLike[];
};

const MIN_SEGMENT_WIDTH = 0.6;

function pct(ms: number, durationMs: number): number {
  if (durationMs <= 0) return 0;
  return Math.max(0, Math.min(100, Math.round((ms / durationMs) * 1000) / 10));
}

function toneFor(label: string): TimelineMoment["tone"] {
  const upper = label.toUpperCase();
  if (upper === "POSITIVE") return "positive";
  if (upper === "NEGATIVE") return "negative";
  return "neutral";
}

export function buildTimeline(input: TimelineInput): { moments: TimelineMoment[]; ticks: TimelineTick[] } {
  const duration = Math.max(1, input.durationMs);
  const moments: TimelineMoment[] = [];

  input.segments.forEach((segment, index) => {
    moments.push({
      id: `segment-${index}`,
      kind: "segment",
      left: pct(segment.offsetMs, duration),
      width: Math.max(MIN_SEGMENT_WIDTH, pct(segment.durationMs, duration)),
      offsetMs: segment.offsetMs,
      title: `${segment.sponsor} on screen, ${describeMinutes(segment.durationMs)}`,
      detail: `${segment.detections} detection${segment.detections === 1 ? "" : "s"}, peak confidence ${segment.peakConfidence.toFixed(2)}`,
      tone: "brand",
    });
  });

  input.voiceMentions.forEach((mention) => {
    moments.push({
      id: `voice-${mention.sentimentEventId}`,
      kind: "voice",
      left: pct(mention.offsetMs, duration),
      width: 0,
      offsetMs: mention.offsetMs,
      title: `Voice mention, ${mention.label.toLowerCase()} ${mention.score.toFixed(2)}`,
      detail: mention.text,
      tone: toneFor(mention.label),
    });
  });

  input.chatMoments.forEach((moment) => {
    const tone: TimelineMoment["tone"] =
      moment.positiveShare >= 0.5 ? "positive" : moment.negativeShare >= 0.5 ? "negative" : "neutral";
    moments.push({
      id: `chat-${moment.offsetMs}`,
      kind: "chat",
      left: pct(moment.offsetMs, duration),
      width: 0,
      offsetMs: moment.offsetMs,
      title: `${moment.count} brand mention${moment.count === 1 ? "" : "s"} in chat, ${Math.round(moment.positiveShare * 100)}% positive`,
      detail: moment.sample ?? "",
      tone,
    });
  });

  input.riskSpikes.forEach((spike) => {
    moments.push({
      id: `risk-${spike.offsetMs}`,
      kind: "risk",
      left: pct(spike.offsetMs, duration),
      width: 0,
      offsetMs: spike.offsetMs,
      title: "Negative spike in chat",
      detail:
        spike.chatNegativeRatio == null
          ? `${spike.chatMessageCount} messages in the minute`
          : `${Math.round(spike.chatNegativeRatio * 100)}% of ${spike.chatMessageCount} messages negative`,
      tone: "negative",
    });
  });

  return { moments, ticks: ticksFor(duration) };
}

/** Ticks every 30 minutes for streams under 4 hours, every hour above, always starting at 0:00. */
export function ticksFor(durationMs: number): TimelineTick[] {
  const stepMs = durationMs > 4 * 3_600_000 ? 3_600_000 : 1_800_000;
  const ticks: TimelineTick[] = [];
  for (let at = 0; at <= durationMs; at += stepMs) {
    ticks.push({ left: pct(at, durationMs), label: formatOffset(at) });
  }
  return ticks;
}

function describeMinutes(ms: number): string {
  const minutes = Math.round(ms / 60_000);
  if (minutes < 1) return `${Math.round(ms / 1000)} seconds`;
  return `${minutes} minute${minutes === 1 ? "" : "s"}`;
}
