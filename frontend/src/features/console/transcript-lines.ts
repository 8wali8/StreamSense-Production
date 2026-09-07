/** Pure merge of transcript segments with their sentiment analyses into one newest-first feed. */

export type TranscriptSegmentLike = {
  segmentId: string;
  text: string;
  startedAt: number;
  transcriptSequence: number;
  source?: string | null;
};

export type TranscriptSentimentLike = {
  segmentId: string;
  text: string;
  segmentStartedAt: number;
  transcriptSequence: number;
  sponsorRelevant: boolean;
};

export type TranscriptLine<TSentiment extends TranscriptSentimentLike> = {
  id: string;
  text: string;
  at: number;
  sequence: number;
  source?: string | null;
  /** General sentiment for the segment, when sentiment-service has processed it. */
  analysis?: TSentiment;
  /** The sponsor-specific analysis, or the general one when it was already sponsor-relevant. */
  sponsorAnalysis?: TSentiment;
};

/**
 * One line per transcript segment, carrying its sentiment when known, plus lines for sentiment
 * events whose segment has not arrived (or has already scrolled out of the segment history).
 */
export function buildTranscriptFeed<TSentiment extends TranscriptSentimentLike>(
  segments: readonly TranscriptSegmentLike[],
  sentiments: readonly TSentiment[],
  sponsorSentiments: readonly TSentiment[],
  limit: number,
): TranscriptLine<TSentiment>[] {
  const sentimentBySegment = new Map(sentiments.map((event) => [event.segmentId, event]));
  const sponsorBySegment = new Map(sponsorSentiments.map((event) => [event.segmentId, event]));
  const segmentIds = new Set(segments.map((segment) => segment.segmentId));

  const sponsorAnalysisFor = (segmentId: string, analysis: TSentiment | undefined): TSentiment | undefined =>
    sponsorBySegment.get(segmentId) ?? (analysis?.sponsorRelevant ? analysis : undefined);

  const fromSegments = segments.map((segment): TranscriptLine<TSentiment> => {
    const analysis = sentimentBySegment.get(segment.segmentId);
    return {
      id: segment.segmentId,
      text: analysis?.text || segment.text,
      at: segment.startedAt,
      sequence: segment.transcriptSequence,
      source: segment.source,
      analysis,
      sponsorAnalysis: sponsorAnalysisFor(segment.segmentId, analysis),
    };
  });

  const fromSentimentOnly = sentiments
    .filter((event) => !segmentIds.has(event.segmentId))
    .map((event): TranscriptLine<TSentiment> => ({
      id: event.segmentId,
      text: event.text,
      at: event.segmentStartedAt,
      sequence: event.transcriptSequence,
      source: undefined,
      analysis: event,
      sponsorAnalysis: sponsorAnalysisFor(event.segmentId, event),
    }));

  return [...fromSegments, ...fromSentimentOnly].sort((left, right) => right.at - left.at).slice(0, limit);
}
