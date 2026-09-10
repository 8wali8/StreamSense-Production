import { formatDuration, formatStart } from "../session/report-format";
import { trendLayout, type TrendInput } from "./trend";

type DealTrendProps = {
  sponsor: string;
  sessions: TrendInput[];
};

const WIDTH = 600;
const HEIGHT = 120;

/** Sponsor time on screen per stream, oldest to newest, as bars. */
export function DealTrend({ sponsor, sessions }: DealTrendProps) {
  const layout = trendLayout(sessions, WIDTH, HEIGHT);
  return (
    <section className="report-card deal-trend" aria-label="Per-stream trend">
      <h2>{sponsor} on screen, per stream</h2>
      {layout.bars.length === 0 ? (
        <div className="empty-state">No streams inside the deal yet.</div>
      ) : (
        <svg
          viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
          preserveAspectRatio="none"
          role="img"
          aria-label="On-screen time per stream"
        >
          {layout.bars.map((bar) => (
            <rect key={bar.id} x={bar.x} y={bar.y} width={bar.width} height={bar.height} rx={2}>
              <title>{`${formatStart(bar.startedAt)}: ${formatDuration(bar.value)}`}</title>
            </rect>
          ))}
        </svg>
      )}
      {layout.bars.length > 0 && (
        <div className="deal-trend-axis">
          <span>{formatStart(layout.bars[0].startedAt)}</span>
          <span>{formatStart(layout.bars[layout.bars.length - 1].startedAt)}</span>
        </div>
      )}
    </section>
  );
}
