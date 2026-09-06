import { ErrorBoundary } from "./components/ErrorBoundary";
import { Health } from "./components/Health";
import { RecommendationPanel } from "./components/RecommendationPanel";
import { SentimentPanel } from "./components/SentimentPanel";
import { SponsorPanel } from "./components/SponsorPanel";
import { StreamMetricsOverview } from "./components/StreamMetricsOverview";
import { TwitchIngestionStatus } from "./components/TwitchIngestionStatus";
import { VideoCaptureStatus } from "./components/VideoCaptureStatus";
import { LiveStreamConsole } from "./features/console/LiveStreamConsole";
import { Roster } from "./features/streamer/Roster";
import { StreamerControls } from "./features/streamer/StreamerControls";
import { useStreamerSelection } from "./features/streamer/useStreamerSelection";

export default function App() {
  const selection = useStreamerSelection();
  const { selectedStreamer, displayBrand, campaignGoal } = selection;

  return (
    <div className="app-shell">
      <aside className="sidebar" aria-label="Primary navigation">
        <div className="brand-lockup">
          <div className="brand-mark">SS</div>
          <div>
            <div className="brand-name">StreamSense</div>
            <div className="brand-kicker">Live sponsor ops</div>
          </div>
        </div>

        <nav className="nav-stack">
          <a className="nav-item nav-item-active" href="#console">Live console</a>
          <a className="nav-item" href="#metrics">Metrics</a>
          <a className="nav-item" href="#evidence">Evidence</a>
          <a className="nav-item" href="#roster">Roster</a>
        </nav>

        <div className="sidebar-card">
          <span className="field-label">Active review</span>
          <strong>@{selectedStreamer}</strong>
          <span>{displayBrand} / {campaignGoal || "No campaign goal"}</span>
        </div>
      </aside>

      <main className="main-stage">
        <header className="command-panel" id="console">
          <div>
            <div className="eyebrow">Twitch sponsor monitoring</div>
            <h1>Live stream console</h1>
            <p>Watch captured stream frames, chat, transcript, sponsor detections, and risk signals in the same operating view.</p>
          </div>

          <div className="command-status-row">
            <Health />
            <TwitchIngestionStatus />
            <VideoCaptureStatus />
          </div>

          <StreamerControls selection={selection} />

          <div className="runtime-channel-note">{selection.channelSwitchStatus}</div>
        </header>

        <ErrorBoundary label="live console">
          <LiveStreamConsole streamer={selectedStreamer} sponsorBrand={displayBrand} campaignGoal={campaignGoal} />
        </ErrorBoundary>

        <section className="metrics-section" id="metrics">
          <ErrorBoundary label="metrics overview">
            <StreamMetricsOverview streamer={selectedStreamer} />
          </ErrorBoundary>
        </section>

        <section className="evidence-grid" id="evidence">
          <ErrorBoundary label="sentiment panel">
            <SentimentPanel streamer={selectedStreamer} hideControls />
          </ErrorBoundary>
          <ErrorBoundary label="sponsor panel">
            <SponsorPanel streamer={selectedStreamer} hideControls />
          </ErrorBoundary>
          <ErrorBoundary label="recommendation panel">
            <RecommendationPanel streamer={selectedStreamer} hideControls />
          </ErrorBoundary>
        </section>

        <Roster selectedStreamer={selectedStreamer} onSelect={(streamer) => void selection.selectPortfolioStreamer(streamer)} />
      </main>
    </div>
  );
}
