import { portfolioStreamers, type PortfolioStreamer } from "./streamer";

type RosterProps = {
  selectedStreamer: string;
  onSelect: (streamer: PortfolioStreamer) => void;
};

/** Quick-switch cards for the watched channels. */
export function Roster({ selectedStreamer, onSelect }: RosterProps) {
  return (
    <section className="roster-section" id="roster">
      <div className="section-heading">
        <div>
          <div className="eyebrow">Watched channels</div>
          <h2>Quick-switch roster</h2>
        </div>
        <span className="status-pill">{portfolioStreamers.length} saved</span>
      </div>
      <div className="portfolio-grid">
        {portfolioStreamers.map((streamer) => (
          <button
            className={`portfolio-card${streamer.handle === selectedStreamer ? " portfolio-card-active" : ""}`}
            key={streamer.handle}
            onClick={() => onSelect(streamer)}
          >
            <span>@{streamer.handle}</span>
            <strong>{streamer.brand}</strong>
            <div className="portfolio-meta">
              <span>{streamer.owner}</span>
              <span>Risk: {streamer.risk}</span>
            </div>
          </button>
        ))}
      </div>
    </section>
  );
}
