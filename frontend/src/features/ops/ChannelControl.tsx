import { useStreamer } from "../streamer/streamer-context";

/** Point chat ingest, video capture, and sponsor relevance at a channel. Operators only. */
export function ChannelControl() {
  const selection = useStreamer();
  return (
    <section className="panel ops-section" aria-label="Channel control">
      <div className="panel-heading">
        <h2>Channel control</h2>
        <p>Chat, video frames, transcript capture, and sponsor relevance follow this channel.</p>
      </div>
      <form
        className="form-row"
        onSubmit={(event) => {
          event.preventDefault();
          void selection.pointRuntimeAtStreamer();
        }}
      >
        <label className="field">
          <span className="field-label">Streamer</span>
          <input
            className="text-input"
            value={selection.streamerInput}
            onChange={(event) => selection.setStreamerInput(event.target.value)}
            placeholder="e.g. redbull-testing"
          />
        </label>
        <label className="field">
          <span className="field-label">Sponsor</span>
          <input
            className="text-input"
            value={selection.sponsorBrand}
            onChange={(event) => selection.setSponsorBrand(event.target.value)}
            placeholder="Red Bull"
          />
        </label>
        <button className="button-primary" type="submit">
          Point capture here
        </button>
      </form>
      <div className="status-line">{selection.channelSwitchStatus}</div>
    </section>
  );
}
