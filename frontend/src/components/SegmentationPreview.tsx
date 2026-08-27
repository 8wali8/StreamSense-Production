import { useState } from "react";

type SegmentableFrame = {
  sourceFrameId: string;
  frameRef: string;
  frameSequence: number;
  capturedAt: number;
};

type RegionProposal = {
  label: string;
  confidence: number;
  x: number;
  y: number;
  width: number;
  height: number;
  source: string;
  areaRatio: number;
};

type SegmentationResponse = {
  modelVersion: string;
  frameWidth: number;
  frameHeight: number;
  proposals: RegionProposal[];
};

type SegmentationPreviewProps = {
  frame?: SegmentableFrame;
};

function clamp(value: number): number {
  return Math.max(0, Math.min(1, value));
}

function toPercent(value: number): string {
  return `${clamp(value) * 100}%`;
}

function frameImageUrl(frameRef: string): string {
  return `/api/video/capture/frame?frameRef=${encodeURIComponent(frameRef)}`;
}

export function SegmentationPreview({ frame }: SegmentationPreviewProps) {
  const [frameRefInput, setFrameRefInput] = useState("");
  const [result, setResult] = useState<SegmentationResponse | null>(null);
  const [segmentedFrameRef, setSegmentedFrameRef] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const activeFrameRef = frameRefInput.trim() || frame?.frameRef || "";
  const displayedFrameRef = segmentedFrameRef ?? activeFrameRef;
  const canSegment = activeFrameRef.length > 0 && !loading;
  const visibleProposals = result?.proposals.slice(0, 12) ?? [];

  async function runSegmentation() {
    if (!activeFrameRef) return;

    const frameRefForRequest = activeFrameRef;
    setLoading(true);
    setError(null);
    setSegmentedFrameRef(frameRefForRequest);

    try {
      const response = await fetch("/ml/segment", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          frameId: frame?.sourceFrameId ?? `preview-${Date.now()}`,
          frameRef: frameRefForRequest,
        }),
      });

      if (!response.ok) {
        throw new Error(`/ml/segment returned ${response.status}`);
      }

      setResult((await response.json()) as SegmentationResponse);
    } catch (err) {
      setResult(null);
      setSegmentedFrameRef(null);
      setError(err instanceof Error ? err.message : "failed to segment frame");
    } finally {
      setLoading(false);
    }
  }

  return (
    <section className="segmentation-panel" aria-label="SAM segmentation preview">
      <div className="segmentation-header">
        <div>
          <span className="eyebrow">SAM segmentation</span>
          <h2>Region proposals</h2>
          <p>Run SAM on the latest captured frame, then inspect candidate sponsor/logo regions.</p>
        </div>
        <span className="status-pill">{result ? `${result.proposals.length} proposals` : "ready"}</span>
      </div>

      <div className="segmentation-controls">
        <label>
          <span className="field-label">Frame ref</span>
          <input
            className="text-input"
            value={frameRefInput}
            onChange={(event) => setFrameRefInput(event.target.value)}
            placeholder={frame?.frameRef ?? "Waiting for a captured frame"}
          />
        </label>
        <button className="button-secondary" disabled={!canSegment} onClick={runSegmentation}>
          {loading ? "Segmenting..." : "Run SAM"}
        </button>
      </div>

      <div className="status-line">
        {frame
          ? `Latest frame #${frame.frameSequence} captured ${new Date(frame.capturedAt).toLocaleTimeString()}`
          : "No captured sponsor frame has arrived yet. Paste a frameRef to test SAM manually."}
        {segmentedFrameRef && ` Showing SAM output for ${segmentedFrameRef}.`}
      </div>

      {error && <div className="error-state" role="alert">{error}</div>}

      <div className="segmentation-layout">
        <div className="segmentation-image-shell">
          {displayedFrameRef ? (
            <>
              <img className="segmentation-image" src={frameImageUrl(displayedFrameRef)} alt="Captured stream frame for segmentation" />
              {visibleProposals.map((proposal, index) => (
                <div
                  className="segmentation-box"
                  key={`${proposal.source}-${index}-${proposal.x}-${proposal.y}`}
                  style={{
                    left: toPercent(proposal.x),
                    top: toPercent(proposal.y),
                    width: toPercent(proposal.width),
                    height: toPercent(proposal.height),
                  }}
                >
                  <span>{index + 1}</span>
                </div>
              ))}
            </>
          ) : (
            <div className="empty-state">Waiting for a frameRef.</div>
          )}
        </div>

        <div className="segmentation-results">
          {result && (
            <div className="event-tags segmentation-model-tags">
              <span className="tag">model={result.modelVersion}</span>
              <span className="tag">size={result.frameWidth}x{result.frameHeight}</span>
            </div>
          )}

          {visibleProposals.length === 0 && <div className="empty-state">Run SAM to show proposals.</div>}

          {visibleProposals.map((proposal, index) => (
            <article className="event-card segmentation-proposal" key={`${proposal.source}-card-${index}-${proposal.areaRatio}`}>
              <div className="event-card-header">
                <strong>Proposal {index + 1}</strong>
                <span className="analysis-chip analysis-live">{proposal.source}</span>
              </div>
              <div className="event-tags">
                <span className="tag">{proposal.label}</span>
                <span className="tag">confidence={proposal.confidence.toFixed(2)}</span>
                <span className="tag">area={(proposal.areaRatio * 100).toFixed(1)}%</span>
                <span className="tag">
                  box={proposal.x.toFixed(2)},{proposal.y.toFixed(2)} {proposal.width.toFixed(2)}x{proposal.height.toFixed(2)}
                </span>
              </div>
            </article>
          ))}
        </div>
      </div>
    </section>
  );
}
