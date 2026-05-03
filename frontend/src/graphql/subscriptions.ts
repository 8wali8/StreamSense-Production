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

export const ON_TRANSCRIPT_SEGMENT_SUBSCRIPTION = gql`
  subscription OnTranscriptSegment($streamer: String!) {
    onTranscriptSegment(streamer: $streamer) {
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

export const ON_TRANSCRIPT_SENTIMENT_SUBSCRIPTION = gql`
  subscription OnTranscriptSentiment($streamer: String!) {
    onTranscriptSentiment(streamer: $streamer) {
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
    }
  }
`;

export const ON_SPONSOR_DETECTION_SUBSCRIPTION = gql`
  subscription OnSponsorDetection($streamer: String!) {
    onSponsorDetection(streamer: $streamer) {
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
