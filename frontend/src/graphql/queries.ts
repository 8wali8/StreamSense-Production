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
