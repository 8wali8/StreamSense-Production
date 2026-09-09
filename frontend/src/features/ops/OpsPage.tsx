import { ErrorBoundary } from "../../components/ErrorBoundary";
import type {
  OnSponsorDetectionSubscription,
  SponsorDetectionsQuery,
  SponsorDetectionsQueryVariables,
} from "../../graphql/generated";
import { RECENT_SPONSOR_DETECTIONS_QUERY } from "../../graphql/queries";
import { ON_SPONSOR_DETECTION_SUBSCRIPTION } from "../../graphql/subscriptions";
import { useLiveFeed } from "../../hooks/useLiveFeed";
import { Health } from "../status/Health";
import { TwitchIngestionStatus } from "../status/TwitchIngestionStatus";
import { VideoCaptureStatus } from "../status/VideoCaptureStatus";
import { useStreamer } from "../streamer/streamer-context";
import { ChannelControl } from "./ChannelControl";
import { SegmentationPreview } from "./SegmentationPreview";
import { SentimentPanel } from "./SentimentPanel";
import { SponsorPanel } from "./SponsorPanel";
import { SponsorProfileEditor } from "./SponsorProfileEditor";
import { StreamMetricsOverview } from "./StreamMetricsOverview";

type SponsorDetectionEvent = SponsorDetectionsQuery["sponsorDetections"][number];

/**
 * Everything an operator needs and a customer never sees: pipeline state, which channel the
 * runtime is pointed at, the sponsor relevance profile, aggregation health, and raw events.
 */
export function OpsPage() {
  const { selectedStreamer } = useStreamer();
  const detections = useLiveFeed<
    SponsorDetectionsQuery,
    OnSponsorDetectionSubscription,
    SponsorDetectionsQueryVariables,
    SponsorDetectionEvent
  >({
    query: RECENT_SPONSOR_DETECTIONS_QUERY,
    variables: { streamer: selectedStreamer, limit: 5 },
    selectHistory: (data) => data.sponsorDetections,
    subscription: ON_SPONSOR_DETECTION_SUBSCRIPTION,
    subscriptionVariables: { streamer: selectedStreamer },
    selectEvent: (data) => data.onSponsorDetection,
    getId: (event) => event.detectionEventId,
    limit: 5,
    resetKey: selectedStreamer,
  });
  const latestFrame = detections.items.find((event) => event.frameRef);

  return (
    <div className="page">
      <header className="page-header">
        <div>
          <div className="eyebrow">Operations</div>
          <h1>Pipeline</h1>
          <p className="page-lede">Internal. Nothing on this page is shown to a streamer or a sponsor.</p>
        </div>
        <div className="status-row" aria-label="Pipeline status">
          <Health />
          <TwitchIngestionStatus />
          <VideoCaptureStatus />
        </div>
      </header>

      <div className="ops-grid">
        <ErrorBoundary label="channel control">
          <ChannelControl />
        </ErrorBoundary>
        <ErrorBoundary label="sponsor profile">
          <SponsorProfileEditor />
        </ErrorBoundary>
      </div>

      <ErrorBoundary label="metrics overview">
        <StreamMetricsOverview streamer={selectedStreamer} />
      </ErrorBoundary>

      <div className="ops-grid">
        <ErrorBoundary label="sentiment events">
          <SentimentPanel streamer={selectedStreamer} />
        </ErrorBoundary>
        <ErrorBoundary label="sponsor detections">
          <SponsorPanel streamer={selectedStreamer} />
        </ErrorBoundary>
      </div>

      <ErrorBoundary label="segmentation preview">
        <SegmentationPreview frame={latestFrame} />
      </ErrorBoundary>
    </div>
  );
}
