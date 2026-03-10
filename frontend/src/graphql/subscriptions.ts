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