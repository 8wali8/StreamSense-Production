import { useChannelMeasurement } from "./useChannelMeasurement";

/**
 * Start and stop measurement of one channel, from the streamer's own home page: the control that
 * makes signing in with Twitch enough to be measured, without an operator pointing the pipeline.
 */
export function MeasureChannel({ channel }: { channel: string }) {
  const { measurement, loading, pending, error, start, stop } = useChannelMeasurement(channel);

  return (
    <section className="panel" aria-label="Measurement">
      <div className="panel-heading">
        <div>
          <h2>Measurement</h2>
          <p>{loading ? `Checking whether @${channel} is being measured…` : measurement.detail}</p>
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
