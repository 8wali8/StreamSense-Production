import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { useQuery, useSubscription } from "@apollo/client/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App";
import {
  RECENT_SENTIMENT_QUERY,
  RECENT_SPONSOR_DETECTIONS_QUERY,
  RECENT_SPONSOR_SENTIMENT_QUERY,
  RECENT_SPONSOR_TRANSCRIPT_SENTIMENT_QUERY,
  RECENT_TRANSCRIPT_SEGMENTS_QUERY,
  RECENT_TRANSCRIPT_SENTIMENT_QUERY,
} from "./graphql/queries";

vi.mock("@apollo/client/react", () => ({
  useQuery: vi.fn(),
  useSubscription: vi.fn(),
}));

vi.mock("./components/Health", () => ({ Health: () => <div>Health: ok</div> }));
vi.mock("./components/RecommendationPanel", () => ({ RecommendationPanel: () => <section>Recommendations</section> }));
vi.mock("./components/SegmentationPreview", () => ({ SegmentationPreview: () => <section>Segmentation</section> }));
vi.mock("./components/SentimentPanel", () => ({ SentimentPanel: () => <section>Sentiment</section> }));
vi.mock("./components/SponsorPanel", () => ({ SponsorPanel: () => <section>Sponsors</section> }));
vi.mock("./components/StreamMetricsOverview", () => ({ StreamMetricsOverview: () => <section>Metrics</section> }));
vi.mock("./components/TwitchIngestionStatus", () => ({ TwitchIngestionStatus: () => <div>Twitch status</div> }));
vi.mock("./components/VideoCaptureStatus", () => ({ VideoCaptureStatus: () => <div>Video status</div> }));

const useQueryMock = vi.mocked(useQuery);
const useSubscriptionMock = vi.mocked(useSubscription);

const redbullSegment = {
  segmentId: "segment-redbull-1",
  streamer: "redbull-testing",
  text: "Red Bull replay transcript stays visible after load.",
  startedAt: 1778734101283,
  endedAt: 1778734103736,
  language: "en",
  confidence: 0.72,
  modelVersion: "faster-whisper-small.en-int8",
  source: "TWITCH_VOD_REPLAY",
  channelLogin: "redbull-testing",
  streamSessionId: "redbull-testing-2750461300",
  twitchStreamId: "2750461300",
  videoTimestampMs: 2436268,
  transcriptSequence: 30,
  captureWorkerId: "video-capture-service-1",
};

const redbullTranscriptSentiment = {
  sentimentEventId: "sentiment-redbull-1",
  segmentId: redbullSegment.segmentId,
  streamer: "redbull-testing",
  text: redbullSegment.text,
  segmentStartedAt: redbullSegment.startedAt,
  segmentEndedAt: redbullSegment.endedAt,
  processedAt: redbullSegment.endedAt + 500,
  label: "POSITIVE",
  score: 0.61,
  modelVersion: "stub-v1",
  transcriptModelVersion: redbullSegment.modelVersion,
  streamSessionId: redbullSegment.streamSessionId,
  transcriptSequence: redbullSegment.transcriptSequence,
  sponsorRelevant: true,
  matchedSponsor: "Red Bull",
};

describe("App live console", () => {
  afterEach(() => {
    vi.clearAllMocks();
    vi.unstubAllGlobals();
  });

  beforeEach(() => {
    useSubscriptionMock.mockReturnValue({ error: undefined } as never);
    useQueryMock.mockImplementation((query, options) => {
      const streamer = (options as { variables?: { streamer?: string } } | undefined)?.variables?.streamer;
      const isRedbull = streamer === "redbull-testing";

      if (query === RECENT_SPONSOR_DETECTIONS_QUERY) {
        return { loading: false, error: undefined, data: { sponsorDetections: [] } } as never;
      }
      if (query === RECENT_TRANSCRIPT_SEGMENTS_QUERY) {
        return { loading: false, error: undefined, data: { recentTranscriptSegments: isRedbull ? [redbullSegment] : [] } } as never;
      }
      if (query === RECENT_SENTIMENT_QUERY) {
        return { loading: false, error: undefined, data: { recentSentiment: [] } } as never;
      }
      if (query === RECENT_TRANSCRIPT_SENTIMENT_QUERY) {
        return {
          loading: false,
          error: undefined,
          data: { recentTranscriptSentiment: isRedbull ? [redbullTranscriptSentiment] : [] },
        } as never;
      }
      if (query === RECENT_SPONSOR_SENTIMENT_QUERY) {
        return { loading: false, error: undefined, data: { recentSponsorSentiment: [] } } as never;
      }
      if (query === RECENT_SPONSOR_TRANSCRIPT_SENTIMENT_QUERY) {
        return {
          loading: false,
          error: undefined,
          data: { recentSponsorTranscriptSentiment: isRedbull ? [redbullTranscriptSentiment] : [] },
        } as never;
      }

      return { loading: false, error: undefined, data: {} } as never;
    });

    vi.stubGlobal(
      "fetch",
      vi.fn(async (input: RequestInfo | URL) => ({
        ok: true,
        status: 200,
        json: async () => (String(input).includes("/api/sentiment/transcript/recent") ? [redbullSegment] : {}),
      })) as never,
    );
  });

  it("keeps all transcript visible after loading redbull replay", async () => {
    render(<App />);

    fireEvent.change(screen.getByDisplayValue("test"), { target: { value: "redbull-testing" } });
    fireEvent.change(screen.getByDisplayValue("Nike"), { target: { value: "Red Bull" } });
    fireEvent.click(screen.getByRole("button", { name: /load console/i }));

    expect(await screen.findByRole("heading", { name: "All transcript" })).toBeInTheDocument();
    expect((await screen.findAllByText(redbullSegment.text)).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/Matched Red Bull/).length).toBeGreaterThan(0);

    await waitFor(() => {
      expect(screen.getByText(/Chat, video frames, transcript capture, and sponsor relevance are pointed at @redbull-testing/)).toBeInTheDocument();
    });
  });
});
