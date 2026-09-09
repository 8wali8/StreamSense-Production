import { gql } from "@apollo/client";

export const HEALTH_QUERY = gql`
  query Health {
    health
  }
`;

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

export const SESSION_QUERY = gql`
  query Session($id: ID!) {
    session(id: $id) {
      id
      streamer
      source
      twitchStreamId
      streamSessionId
      channelLogin
      title
      category
      startedAt
      endedAt
      live
      durationMs
      peakViewers
      averageViewers
      viewerSamples
    }
  }
`;

export const SESSIONS_QUERY = gql`
  query Sessions($streamer: String!, $from: Float, $to: Float, $limit: Int) {
    sessions(streamer: $streamer, from: $from, to: $to, limit: $limit) {
      id
      streamer
      source
      twitchStreamId
      title
      category
      startedAt
      endedAt
      live
      durationMs
      peakViewers
      averageViewers
      viewerSamples
    }
  }
`;

export const SESSION_SUMMARY_QUERY = gql`
  query SessionSummary(
    $sessionId: ID!
    $sponsor: String
    $chatCommand: String
    $trackedLinkHost: String
    $cpmPer30sEquivalent: Float
    $hostReadRatePer1000: Float
  ) {
    sessionSummary(
      sessionId: $sessionId
      sponsor: $sponsor
      chatCommand: $chatCommand
      trackedLinkHost: $trackedLinkHost
      cpmPer30sEquivalent: $cpmPer30sEquivalent
      hostReadRatePer1000: $hostReadRatePer1000
    ) {
      session {
        id
        streamer
        source
        twitchStreamId
        streamSessionId
        title
        category
        startedAt
        endedAt
        live
        durationMs
        peakViewers
        averageViewers
        viewerSamples
      }
      dealId
      sponsor
      onScreenMs
      onScreenShare
      mentions
      chatMentions
      voiceMentions
      mentionSentiment
      mentionPositiveShare
      mentionNegativeShare
      averageViewers
      peakViewers
      risk {
        level
        score
        factors {
          name
          value
          weight
        }
      }
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
      engagement {
        spikeCount
        latestSpikeAt
      }
      value {
        logoValue
        hostReadValue
        mediaValue
        weightedLogoViewerMinutes
        averageProminence
        cpmPer30sEquivalent
        hostReadRatePer1000
        basis
      }
      response {
        chatCommand
        commandUses
        commandUsers
        trackedLinkHost
        linkPosts
      }
    }
  }
`;

export const SPONSOR_MOMENTS_QUERY = gql`
  query SponsorMoments($sessionId: ID!, $sponsor: String) {
    sponsorMoments(sessionId: $sessionId, sponsor: $sponsor) {
      sponsor
      session {
        id
        startedAt
        durationMs
      }
      segments {
        sponsor
        startedAt
        endedAt
        offsetMs
        durationMs
        videoTimestampMs
        detections
        peakConfidence
      }
      voiceMentions {
        sentimentEventId
        at
        offsetMs
        label
        score
        text
      }
      chatMoments {
        at
        offsetMs
        count
        positiveShare
        negativeShare
        averageScore
        sample
        user
      }
      riskSpikes {
        at
        offsetMs
        chatNegativeRatio
        chatMessageCount
      }
      best {
        kind
        at
        offsetMs
        title
        detail
        score
      }
      weakest {
        kind
        at
        offsetMs
        title
        detail
        score
      }
    }
  }
`;

export const DEALS_QUERY = gql`
  query Deals($streamer: String, $limit: Int) {
    deals(streamer: $streamer, limit: $limit) {
      id
      streamer
      sponsor
      startsAt
      endsAt
      promisedStreams
      fee
      currency
      cpmPer30sEquivalent
      hostReadRatePer1000
      trackedLink
      trackedLinkHost
      chatCommand
      channelPointReward
      active
      createdAt
    }
  }
`;

export const DEAL_QUERY = gql`
  query Deal($id: ID!) {
    deal(id: $id) {
      id
      streamer
      sponsor
      startsAt
      endsAt
      promisedStreams
      fee
      currency
      cpmPer30sEquivalent
      hostReadRatePer1000
      trackedLink
      trackedLinkHost
      chatCommand
      channelPointReward
      active
      createdAt
    }
  }
`;

export const DEAL_SUMMARY_QUERY = gql`
  query DealSummary($id: ID!) {
    dealSummary(id: $id) {
      deal {
        id
        streamer
        sponsor
        startsAt
        endsAt
        promisedStreams
        fee
        currency
        cpmPer30sEquivalent
        hostReadRatePer1000
        trackedLink
        trackedLinkHost
        chatCommand
        channelPointReward
        active
        createdAt
      }
      totals {
        streams
        liveStreams
        streamedMs
        onScreenMs
        onScreenShare
        mentions
        chatMentions
        voiceMentions
        mentionSentiment
        averageViewers
        logoValue
        hostReadValue
        mediaValue
        commandUses
        linkPosts
      }
      sessions {
        session {
          id
          title
          startedAt
          endedAt
          live
          durationMs
          averageViewers
          peakViewers
        }
        onScreenMs
        onScreenShare
        mentions
        mentionSentiment
        averageViewers
        risk {
          level
        }
        value {
          mediaValue
        }
      }
    }
  }
`;
