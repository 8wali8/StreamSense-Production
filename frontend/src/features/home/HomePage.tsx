import { ErrorBoundary } from "../../components/ErrorBoundary";
import { LiveStreamConsole } from "../console/LiveStreamConsole";
import { useStreamer } from "../streamer/streamer-context";

/** The streamer's home: the live console for the channel the runtime is pointed at. */
export function HomePage() {
  const { selectedStreamer, displayBrand } = useStreamer();

  return (
    <div className="page">
      <header className="page-header">
        <div>
          <div className="eyebrow">Home</div>
          <h1>@{selectedStreamer}</h1>
        </div>
        <span className="pill pill-teal">{displayBrand}</span>
      </header>

      <ErrorBoundary label="live console">
        <LiveStreamConsole streamer={selectedStreamer} sponsorBrand={displayBrand} />
      </ErrorBoundary>
    </div>
  );
}
