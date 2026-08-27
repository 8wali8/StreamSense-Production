import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { SegmentationPreview } from "./SegmentationPreview";

const frame = {
  sourceFrameId: "frame-1",
  frameRef: "s3://streamsense-frames/test.png",
  frameSequence: 7,
  capturedAt: 1710000000000,
};

const newerFrame = {
  sourceFrameId: "frame-2",
  frameRef: "s3://streamsense-frames/newer.png",
  frameSequence: 8,
  capturedAt: 1710000010000,
};

describe("SegmentationPreview", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("runs segmentation for the latest frame", async () => {
    vi.mocked(fetch).mockResolvedValue({
      ok: true,
      json: async () => ({
        modelVersion: "sam-vit-b",
        frameWidth: 1280,
        frameHeight: 720,
        proposals: [
          {
            label: "region",
            confidence: 0.96,
            x: 0.1,
            y: 0.2,
            width: 0.3,
            height: 0.4,
            source: "sam",
            areaRatio: 0.12,
          },
        ],
      }),
    } as Response);

    const { rerender } = render(<SegmentationPreview frame={frame} />);

    fireEvent.click(screen.getByRole("button", { name: "Run SAM" }));

    await waitFor(() => expect(fetch).toHaveBeenCalledWith(
      "/ml/segment",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ frameId: "frame-1", frameRef: "s3://streamsense-frames/test.png" }),
      }),
    ));
    expect(await screen.findByText("Proposal 1")).toBeInTheDocument();
    expect(screen.getByText("model=sam-vit-b")).toBeInTheDocument();
    expect(screen.getByText("confidence=0.96")).toBeInTheDocument();
    expect(screen.getByRole("img", { name: "Captured stream frame for segmentation" })).toHaveAttribute(
      "src",
      "/api/video/capture/frame?frameRef=s3%3A%2F%2Fstreamsense-frames%2Ftest.png",
    );

    rerender(<SegmentationPreview frame={newerFrame} />);

    expect(screen.getByRole("img", { name: "Captured stream frame for segmentation" })).toHaveAttribute(
      "src",
      "/api/video/capture/frame?frameRef=s3%3A%2F%2Fstreamsense-frames%2Ftest.png",
    );
  });

  it("renders segmentation errors", async () => {
    vi.mocked(fetch).mockResolvedValue({ ok: false, status: 503 } as Response);

    render(<SegmentationPreview frame={frame} />);

    fireEvent.click(screen.getByRole("button", { name: "Run SAM" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("/ml/segment returned 503");
  });
});
