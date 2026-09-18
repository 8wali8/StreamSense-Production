import { useChannelMeasurement } from "./useChannelMeasurement";

/**
 * Start and stop measurement of one channel, from the streamer's own home page: the control that
 * makes signing in with Twitch enough to be measured, without an operator pointing the pipeline.
 */
type Props = {
  channel: string;
  /** Whether the channel is live right now; null while that is not known. Measurement of an offline channel is idle. */
  channelLive?: boolean | null;
};

export function MeasureChannel({ channel, channelLive = null }: Props) {
  const { measurement, loading, pending, error, start, stop } = useChannelMeasurement(channel);
  const detail =
    measurement.measuring && channelLive === false
      ? `${measurement.detail} Nothing arrives until @${channel} goes live.`
      : measurement.detail;

  return (
    <section className="panel" aria-label="Measurement">
      <div className="panel-heading">
        <div>
          <h2>Measurement</h2>
          <p>{loading ? `Checking whether @${channel} is being measured…` : detail}</p>
        </div>
        {!loading && (
          <button
            className={measurement.measuring ? "button-secondary button-sm" : "button-primary button-sm"}
            type="button"
            disabled={pending || measurement.unavailable}
            onClick={() => void (measurement.measuring ? stop() : start())}
          >
            {pending
              ? measurement.measuring
                ? "Stopping…"
                : "Starting…"
              : measurement.measuring
                ? "Stop measuring"
                : "Start measuring"}
          </button>
        )}
      </div>
      {error && (
        <div className="error-state" role="alert">
          {error}
        </div>
      )}
    </section>
  );
}
