import { useState } from "react";
import { Health } from "./components/Health";
import { RecommendationPanel } from "./components/RecommendationPanel";
import { SentimentPanel } from "./components/SentimentPanel";
import { SponsorPanel } from "./components/SponsorPanel";
import { LiveChat } from "./pages/LiveChat";

type PortfolioStreamer = {
  handle: string;
  brand: string;
  sentiment: string;
  visibility: string;
  risk: string;
};

const portfolioStreamers: PortfolioStreamer[] = [
  { handle: "test", brand: "Nike", sentiment: "+18%", visibility: "Strong", risk: "Low" },
  { handle: "speedrun-lab", brand: "Prime", sentiment: "+11%", visibility: "Medium", risk: "Watch" },
  { handle: "arena-night", brand: "Razer", sentiment: "-4%", visibility: "Strong", risk: "Medium" },
];

const pipelineStages = [
  "Audience chat ingest",
  "Sentiment scoring",
  "Sponsor visibility scan",
  "Recommendation synthesis",
];

const historicalRuns = [
  { label: "Last 24h", sentiment: "+18%", exposure: "92%", action: "Increase activation" },
  { label: "7 days", sentiment: "+9%", exposure: "74%", action: "Maintain spend" },
  { label: "30 days", sentiment: "+14%", exposure: "81%", action: "Renew sponsor slot" },
];

export default function App() {
  const [streamerInput, setStreamerInput] = useState("test");
  const [selectedStreamer, setSelectedStreamer] = useState("test");
  const [sponsorBrand, setSponsorBrand] = useState("Nike");
  const [campaignGoal, setCampaignGoal] = useState("Launch-week brand lift");
  const [analysisRuns, setAnalysisRuns] = useState(1);

  function analyzeStreamer() {
    const nextStreamer = streamerInput.trim();
    if (!nextStreamer) {
      return;
    }

    setSelectedStreamer(nextStreamer);
    setAnalysisRuns((current) => current + 1);
  }

  const activePortfolio = portfolioStreamers.find((streamer) => streamer.handle === selectedStreamer);
  const displayBrand = sponsorBrand.trim() || activePortfolio?.brand || "Sponsor brand";

  return (
    <div className="app-shell">
      <aside className="sidebar" aria-label="Primary navigation">
        <div className="brand-lockup">
          <div className="brand-mark">SS</div>
          <div>
            <div className="brand-name">StreamSense</div>
            <div className="brand-kicker">Sponsor intelligence</div>
          </div>
        </div>

        <nav className="nav-stack">
          <a className="nav-item nav-item-active" href="#dashboard">Dashboard</a>
          <a className="nav-item" href="#portfolio">Streamers</a>
          <a className="nav-item" href="#analytics">Past analytics</a>
          <a className="nav-item" href="#evidence">Evidence feed</a>
        </nav>

        <div className="sidebar-card">
          <div className="eyebrow">Current campaign</div>
          <strong>{displayBrand}</strong>
          <span>{campaignGoal || "Campaign goal not set"}</span>
        </div>
      </aside>

      <main className="main-stage">
        <header className="hero-panel" id="dashboard">
          <div className="hero-copy">
            <div className="eyebrow">Sponsor Command Center</div>
            <h1>Turn streamer moments into sponsor decisions.</h1>
            <p>
              Enter a Twitch streamer, run the StreamSense pipeline, and review audience sentiment,
              sponsor visibility, and recommended campaign actions in one place.
            </p>

            <div className="health-strip">
              <Health />
              <span className="status-pill status-live">Live demo stack</span>
              <span className="status-pill">Mock sponsor workflow</span>
            </div>
          </div>

          <section className="analysis-card" aria-label="Analyze streamer">
            <div className="analysis-card-header">
              <span className="orb" />
              <div>
                <h2>Analyze a sponsored streamer</h2>
                <p>Use a Twitch handle now; real ingestion wiring can replace the demo data path later.</p>
              </div>
            </div>

            <label className="field-label" htmlFor="streamer-handle">Twitch streamer</label>
            <div className="search-row">
              <input
                id="streamer-handle"
                className="text-input text-input-large"
                value={streamerInput}
                onChange={(event) => setStreamerInput(event.target.value)}
                placeholder="e.g. shroud"
              />
              <button className="button-primary" onClick={analyzeStreamer}>Analyze Streamer</button>
            </div>

            <div className="form-grid">
              <label>
                <span className="field-label">Sponsor brand</span>
                <input
                  className="text-input"
                  value={sponsorBrand}
                  onChange={(event) => setSponsorBrand(event.target.value)}
                  placeholder="Nike, Prime, Razer"
                />
              </label>
              <label>
                <span className="field-label">Campaign goal</span>
                <input
                  className="text-input"
                  value={campaignGoal}
                  onChange={(event) => setCampaignGoal(event.target.value)}
                  placeholder="Brand lift, renewal, risk review"
                />
              </label>
            </div>
          </section>
        </header>

        <section className="pipeline-panel" aria-label="Analysis pipeline status">
          {pipelineStages.map((stage, index) => (
            <div className="pipeline-step" key={stage}>
              <span className="pipeline-index">0{index + 1}</span>
              <div>
                <strong>{stage}</strong>
                <p>{analysisRuns > 1 ? "Complete for current run" : "Ready when analysis starts"}</p>
              </div>
            </div>
          ))}
        </section>

        <section className="summary-grid" aria-label="Executive summary">
          <SummaryCard label="Streamer" value={`@${selectedStreamer}`} detail="Selected sponsor target" tone="cyan" />
          <SummaryCard label="Audience mood" value="Positive" detail="Recent chat skews constructive" tone="green" />
          <SummaryCard label="Sponsor visibility" value="Strong" detail={`${displayBrand} has clean detection signals`} tone="violet" />
          <SummaryCard label="Recommended action" value="Increase" detail="Lean into high-engagement segments" tone="amber" />
        </section>

        <section className="content-grid">
          <div className="content-column content-column-wide">
            <SentimentPanel key={`sentiment-${selectedStreamer}-${analysisRuns}`} streamer={selectedStreamer} hideControls />
            <LiveChat key={`chat-${selectedStreamer}-${analysisRuns}`} streamer={selectedStreamer} autoConnect hideControls />
          </div>
          <div className="content-column">
            <SponsorPanel key={`sponsor-${selectedStreamer}-${analysisRuns}`} streamer={selectedStreamer} hideControls />
            <RecommendationPanel key={`recommendations-${selectedStreamer}-${analysisRuns}`} streamer={selectedStreamer} hideControls />
          </div>
        </section>

        <section className="portfolio-section" id="portfolio">
          <div className="section-heading">
            <div>
              <div className="eyebrow">Sponsored roster</div>
              <h2>Choose streamers to review</h2>
            </div>
            <span className="status-pill">{portfolioStreamers.length} tracked streamers</span>
          </div>

          <div className="portfolio-grid">
            {portfolioStreamers.map((streamer) => (
              <button
                className={`portfolio-card${streamer.handle === selectedStreamer ? " portfolio-card-active" : ""}`}
                key={streamer.handle}
                onClick={() => {
                  setStreamerInput(streamer.handle);
                  setSelectedStreamer(streamer.handle);
                  setSponsorBrand(streamer.brand);
                  setAnalysisRuns((current) => current + 1);
                }}
              >
                <span>@{streamer.handle}</span>
                <strong>{streamer.brand}</strong>
                <div className="portfolio-meta">
                  <span>{streamer.sentiment} sentiment</span>
                  <span>{streamer.visibility} visibility</span>
                  <span>{streamer.risk} risk</span>
                </div>
              </button>
            ))}
          </div>
        </section>

        <section className="history-section" id="analytics">
          <div className="section-heading">
            <div>
              <div className="eyebrow">Past analytics</div>
              <h2>Use prior runs to guide sponsor work</h2>
            </div>
            <span className="status-pill">Demo trend model</span>
          </div>

          <div className="history-grid">
            {historicalRuns.map((run) => (
              <article className="history-card" key={run.label}>
                <span>{run.label}</span>
                <strong>{run.action}</strong>
                <div className="history-metrics">
                  <span>Sentiment {run.sentiment}</span>
                  <span>Exposure {run.exposure}</span>
                </div>
              </article>
            ))}
          </div>
        </section>
      </main>
    </div>
  );
}

function SummaryCard({ label, value, detail, tone }: { label: string; value: string; detail: string; tone: string }) {
  return (
    <article className={`summary-card summary-${tone}`}>
      <span>{label}</span>
      <strong>{value}</strong>
      <p>{detail}</p>
    </article>
  );
}
