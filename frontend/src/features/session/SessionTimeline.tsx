import { useState } from "react";
import { formatOffset, vodUrl } from "./report-format";
import { buildTimeline, type TimelineInput, type TimelineMoment } from "./timeline";

type SessionTimelineProps = {
  sponsor: string;
  session: { source: string; twitchStreamId?: string | null };
  input: TimelineInput;
};

const LANES: Array<{ kind: TimelineMoment["kind"]; label: string }> = [
  { kind: "segment", label: "On screen" },
  { kind: "voice", label: "Voice" },
  { kind: "chat", label: "Chat" },
  { kind: "risk", label: "Risk" },
];

/** One time axis with four lanes. Clicking a moment shows it below with a link into the recording. */
export function SessionTimeline({ sponsor, session, input }: SessionTimelineProps) {
  const { moments, ticks } = buildTimeline(input);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const selected =
    moments.find((moment) => moment.id === selectedId) ?? moments.find((m) => m.kind === "segment") ?? null;
  const link = selected ? vodUrl(session, selected.offsetMs) : null;

  return (
    <section className="report-card timeline" aria-label={`Where ${sponsor} showed up`}>
      <div className="timeline-heading">
        <h2>Where {sponsor} showed up</h2>
        <div className="timeline-legend">
          <span>
            <i className="legend-seg" /> On screen
          </span>
          <span>
            <i className="legend-dot tone-brand" /> Voice
          </span>
          <span>
            <i className="legend-dot tone-neutral" /> Chat
          </span>
          <span>
            <i className="legend-dot tone-negative" /> Risk
          </span>
        </div>
      </div>

      <div className="timeline-axis">
        <div className="timeline-ticks">
          {ticks.map((tick) => (
            <span className="tick" style={{ left: `${tick.left}%` }} key={tick.label}>
              {tick.label}
            </span>
          ))}
        </div>
        {LANES.map((lane) => (
          <div className="lane" key={lane.kind}>
            <span className="lane-label">{lane.label}</span>
            <div className="lane-track">
              {moments
                .filter((moment) => moment.kind === lane.kind)
                .map((moment) => (
                  <button
                    type="button"
                    key={moment.id}
                    className={`${moment.kind === "segment" ? "seg" : "mark"} tone-${moment.tone}${
                      moment.id === selected?.id ? " selected" : ""
                    }`}
                    style={
                      moment.kind === "segment"
                        ? { left: `${moment.left}%`, width: `${moment.width}%` }
                        : { left: `${moment.left}%` }
                    }
                    title={moment.title}
                    aria-label={`${formatOffset(moment.offsetMs)} ${moment.title}`}
                    onClick={() => setSelectedId(moment.id)}
                  />
                ))}
            </div>
          </div>
        ))}
      </div>

      {selected ? (
        <div className="timeline-selected">
          <span className="mono">{formatOffset(selected.offsetMs)}</span>
          <div>
            <strong>{selected.title}</strong>
            {selected.detail && <p>{selected.detail}</p>}
          </div>
          {link && (
            <a href={link} target="_blank" rel="noreferrer">
              Open VOD
            </a>
          )}
        </div>
      ) : (
        <div className="timeline-selected muted-text">No {sponsor} moments were recorded in this stream.</div>
      )}
    </section>
  );
}
