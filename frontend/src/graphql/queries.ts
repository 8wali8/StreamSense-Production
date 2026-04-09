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
    }
  }
`;
