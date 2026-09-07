import type { StreamerSelection } from "./useStreamerSelection";

/** The streamer / sponsor / campaign form at the top of the console. */
export function StreamerControls({ selection }: { selection: StreamerSelection }) {
  return (
    <div className="command-form" aria-label="Stream analysis controls">
      <label>
        <span className="field-label">Streamer</span>
        <input
          className="text-input"
          value={selection.streamerInput}
          onChange={(event) => selection.setStreamerInput(event.target.value)}
          placeholder="e.g. test"
        />
      </label>

      <label>
        <span className="field-label">Sponsor</span>
        <input
          className="text-input"
          value={selection.sponsorBrand}
          onChange={(event) => selection.setSponsorBrand(event.target.value)}
          placeholder="Nike, Red Bull, Razer"
        />
      </label>

      <label>
        <span className="field-label">Campaign goal</span>
        <input
          className="text-input"
          value={selection.campaignGoal}
          onChange={(event) => selection.setCampaignGoal(event.target.value)}
          placeholder="Brand lift, renewal, risk review"
        />
      </label>
      <button className="button-primary" onClick={() => void selection.analyzeStreamer()}>Load Console</button>
    </div>
  );
}
