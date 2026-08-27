import { gql } from "@apollo/client";

export const RECENT_SENTIMENT_QUERY = gql`
  query RecentSentiment($streamer: String!, $limit: Int!) {
    recentSentiment(streamer: $streamer, limit: $limit) {
      sentimentEventId
      sourceEventId
      streamer
      user
      message
      chatTimestamp
      processedAt
      label
      score
      modelVersion
      sponsorRelevant
      matchedSponsor
      matchedTerms
      relevanceScore
      relevanceReason
      relevanceVersion
    }
  }
`;

export const RECENT_SPONSOR_SENTIMENT_QUERY = gql`
  query RecentSponsorSentiment($streamer: String!, $sponsor: String, $limit: Int!) {
    recentSponsorSentiment(streamer: $streamer, sponsor: $sponsor, limit: $limit) {
      sentimentEventId
      sourceEventId
      streamer
      user
      message
      chatTimestamp
      processedAt
      label
      score
      modelVersion
      sponsorRelevant
      matchedSponsor
      matchedTerms
      relevanceScore
      relevanceReason
      relevanceVersion
    }
  }
`;

export const RECENT_TRANSCRIPT_SEGMENTS_QUERY = gql`
  query RecentTranscriptSegments($streamer: String!, $limit: Int!) {
    recentTranscriptSegments(streamer: $streamer, limit: $limit) {
      segmentId
      streamer
      text
      startedAt
      endedAt
      language
      confidence
      modelVersion
      source
      channelLogin
      streamSessionId
      videoTimestampMs
      transcriptSequence
      captureWorkerId
    }
  }
`;

export const RECENT_TRANSCRIPT_SENTIMENT_QUERY = gql`
  query RecentTranscriptSentiment($streamer: String!, $limit: Int!) {
    recentTranscriptSentiment(streamer: $streamer, limit: $limit) {
      sentimentEventId
      segmentId
      streamer
      text
      segmentStartedAt
      segmentEndedAt
      processedAt
      label
      score
      modelVersion
      transcriptModelVersion
      streamSessionId
      transcriptSequence
      sponsorRelevant
      matchedSponsor
      matchedTerms
      relevanceScore
      relevanceReason
      relevanceVersion
    }
  }
`;

export const RECENT_SPONSOR_TRANSCRIPT_SENTIMENT_QUERY = gql`
  query RecentSponsorTranscriptSentiment($streamer: String!, $sponsor: String, $limit: Int!) {
    recentSponsorTranscriptSentiment(streamer: $streamer, sponsor: $sponsor, limit: $limit) {
      sentimentEventId
      segmentId
      streamer
      text
      segmentStartedAt
      segmentEndedAt
      processedAt
      label
      score
      modelVersion
      transcriptModelVersion
      streamSessionId
      transcriptSequence
      sponsorRelevant
      matchedSponsor
      matchedTerms
      relevanceScore
      relevanceReason
      relevanceVersion
    }
  }
`;

export const RECENT_SPONSOR_DETECTIONS_QUERY = gql`
  query SponsorDetections($streamer: String!, $limit: Int!) {
    sponsorDetections(streamer: $streamer, limit: $limit) {
      detectionEventId
      sourceFrameId
      streamer
      frameRef
      frameSequence
      capturedAt
      processedAt
      sponsor
      confidence
      modelVersion
      x
      y
      width
      height
      source
      channelLogin
      streamSessionId
      twitchStreamId
      videoTimestampMs
    }
  }
`;

export const RECOMMENDATIONS_QUERY = gql`
  query Recommendations($streamer: String!, $limit: Int!) {
    recommendations(streamer: $streamer, limit: $limit) {
      recommendationId
      streamer
      title
      category
      score
      reasonSummary
      reasons
      experimentName
      variantId
      generatedAt
    }
  }
`;

export const STREAM_ANALYTICS_QUERY = gql`
  query StreamAnalytics($streamer: String!, $windowMinutes: Int!, $bucketSeconds: Int!) {
    streamMetricsSummary(streamer: $streamer, windowMinutes: $windowMinutes) {
      streamer
      streamSessionId
      windowMinutes
      bucketSizeSeconds
      windowStart
      windowEnd
      chat {
        totalMessages
        messagesPerMinute
        uniqueChatters
        peakMessagesPerMinute
      }
      chatSentiment {
        positive
        neutral
        negative
        averageScore
        negativeRatio
      }
      transcriptSentiment {
        positive
        neutral
        negative
        averageScore
        negativeRatio
      }
      sponsorExposure {
        totalDetections
        acceptedDetections
        estimatedExposureMs
        topSponsors {
          sponsor
          detectionCount
          acceptedDetectionCount
          estimatedExposureMs
          averageConfidence
          maxConfidence
          fallbackDetectionCount
          lowConfidenceDetectionCount
        }
      }
      engagement {
        spikeCount
        latestSpikeAt
      }
      risk {
        level
        score
        factors {
          name
          value
          weight
        }
      }
      dataQuality {
        lowData
        latestEventAt
        aggregationLagMs
      }
    }
    streamMetricsTimeseries(streamer: $streamer, windowMinutes: $windowMinutes, bucketSeconds: $bucketSeconds) {
      bucketStart
      bucketEnd
      chatMessageCount
      uniqueChatters
      chatAverageScore
      chatNegativeRatio
      transcriptAverageScore
      transcriptNegativeRatio
      sponsorDetectionCount
      estimatedSponsorExposureMs
      engagementSpike
      negativeSpike
    }
  }
`;
