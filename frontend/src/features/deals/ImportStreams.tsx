import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router";
import { importVod, listVods, type VodListing } from "../../api/analytics";
import { getVideoCaptureStatus } from "../../api/video";
import { usePolledResource } from "../../hooks/usePolledResource";
import { describeError } from "../../lib/errors";
import { formatCount, formatDuration, formatStart } from "../session/report-format";
import { importLabel, recordingsForDeal } from "./import-streams";

type ImportStreamsProps = {
  streamer: string;
  /** The deal's dates; recordings from a day before the start onwards are offered. */
  startsAt: number;
  endsAt: number | null;
  /** Called when an import was started, so the deal's numbers refetch. */
  onImported: () => void;
};

/**
 * S8: a streamer who joins with a deal already running pulls the earlier streams in from their
 * Twitch recordings. Each import replays the recording's chat, frames, and audio through the
 * pipeline with the original timestamps, so the report is computed the same way as for a live
 * stream. Twitch keeps no concurrent viewer history, so the streamer may enter the stream's average
 * viewers from their dashboard; without it the value tiles stay empty for that stream.
 */
export function ImportStreams({ streamer, startsAt, endsAt, onImported }: ImportStreamsProps) {
  const [vods, setVods] = useState<VodListing[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [showAll, setShowAll] = useState(false);
  const [viewers, setViewers] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState<string | null>(null);
  const [started, setStarted] = useState<Record<string, string[]>>({});
  const capture = usePolledResource(getVideoCaptureStatus, 10_000, streamer);
  const imports = new Map((capture.data?.imports ?? []).map((status) => [status.vodId, status]));

  const load = useCallback(async () => {
    try {
      setVods(await listVods(streamer, 25));
      setError(null);
    } catch (err) {
      setError(describeError(err instanceof Error ? err : new Error("failed to list recordings")));
    }
  }, [streamer]);

  useEffect(() => {
    void load();
  }, [load]);

  async function start(vod: VodListing) {
    const raw = viewers[vod.vodId]?.trim() ?? "";
    const averageViewers = raw === "" ? undefined : Number(raw);
    setBusy(vod.vodId);
    try {
      const result = await importVod(streamer, vod.vodId, Number.isFinite(averageViewers) ? averageViewers : undefined);
      setStarted((current) => ({ ...current, [vod.vodId]: result.problems }));
      await load();
      onImported();
    } catch (err) {
      setError(describeError(err instanceof Error ? err : new Error("import failed")));
    } finally {
      setBusy(null);
    }
  }

  const rows = recordingsForDeal(vods ?? [], startsAt, endsAt, showAll);

  return (
    <section className="panel past-streams import-streams" aria-label="Earlier streams on Twitch">
      <div className="panel-heading deals-heading">
        <div>
          <h2>Earlier streams on Twitch</h2>
          <p>Recordings Twitch still has. Importing one replays it through the pipeline with its original times.</p>
        </div>
        <label className="check">
          <input type="checkbox" checked={showAll} onChange={(event) => setShowAll(event.target.checked)} /> Show all
          recordings
        </label>
      </div>
      {error && (
        <div className="error-state" role="alert">
          {error}
        </div>
      )}
      {vods === null && !error && <div className="empty-state">Looking up recordings...</div>}
      {vods !== null && rows.length === 0 && (
        <div className="empty-state">
          {showAll ? "Twitch has no recordings for this channel." : "No recordings inside the deal's dates."}
        </div>
      )}
      {rows.map((vod) => {
        const status = imports.get(vod.vodId);
        const label = importLabel(status);
        const problems = started[vod.vodId] ?? [];
        return (
          <div className="vod-row" key={vod.vodId}>
            <span>
              <strong>{vod.title ?? "Untitled recording"}</strong>
              <small>
                {formatStart(vod.createdAt)} · {formatDuration(vod.durationMs)} · {formatCount(vod.viewCount)} views on
                Twitch
              </small>
              {problems.length > 0 && <small className="tone-warn">{problems.join("; ")}</small>}
            </span>
            {vod.sessionId != null ? (
              <span className="vod-actions">
                {label && <em className="tone-muted">{label}</em>}
                <Link className="button-secondary button-sm" to={`/sessions/${vod.sessionId}`}>
                  Open report
                </Link>
              </span>
            ) : (
              <span className="vod-actions">
                <input
                  className="text-input viewers-input"
                  inputMode="numeric"
                  aria-label={`Average viewers for ${vod.title ?? vod.vodId}`}
                  placeholder="avg viewers"
                  value={viewers[vod.vodId] ?? ""}
                  onChange={(event) => setViewers((current) => ({ ...current, [vod.vodId]: event.target.value }))}
                />
                <button
                  className="button-primary button-sm"
                  type="button"
                  disabled={busy !== null}
                  onClick={() => void start(vod)}
                >
                  {busy === vod.vodId ? "Starting..." : "Import"}
                </button>
              </span>
            )}
          </div>
        );
      })}
    </section>
  );
}
