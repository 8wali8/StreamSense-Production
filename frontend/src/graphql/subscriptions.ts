import { gql } from "@apollo/client";

export const ON_CHAT_MESSAGE_SUBSCRIPTION = gql`
  subscription OnChatMessage($streamer: String!) {
    onChatMessage(streamer: $streamer) {
      eventId
      streamer
      user
      message
      timestamp
    }
  }
`;

export const ON_SENTIMENT_SUBSCRIPTION = gql`
  subscription OnSentiment($streamer: String!) {
    onSentiment(streamer: $streamer) {
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
