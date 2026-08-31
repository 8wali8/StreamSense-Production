import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { HttpResponse, restProblem, restResolver, server } from "../../test/msw";
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

const segmentation = {
  modelVersion: "sam-vit-b",
  frameWidth: 1280,
  frameHeight: 720,
  proposals: [
    { label: "Proposal 1", confidence: 0.9, x: 0.1, y: 0.1, width: 0.2, height: 0.2, source: "sam", areaRatio: 0.04 },
  ],
};

describe("SegmentationPreview", () => {
  it("runs segmentation for the latest frame and keeps showing the segmented frame", async () => {
    let requestBody: unknown;
    server.use(
      restResolver("post", "/ml/segment", async ({ request }) => {
        requestBody = await request.json();
        return HttpResponse.json(segmentation);
      }),
    );
    const user = userEvent.setup();

    const { rerender } = render(<SegmentationPreview frame={frame} />);
    await user.click(screen.getByRole("button", { name: "Run SAM" }));

    expect((await screen.findAllByText("Proposal 1")).length).toBeGreaterThan(0);
    expect(requestBody).toEqual({ frameId: "frame-1", frameRef: "s3://streamsense-frames/test.png" });
    expect(screen.getByText("model=sam-vit-b")).toBeInTheDocument();
    expect(screen.getByRole("img", { name: /captured stream frame/i })).toHaveAttribute(
      "src",
      "/api/video/capture/frame?frameRef=s3%3A%2F%2Fstreamsense-frames%2Ftest.png",
    );

    rerender(<SegmentationPreview frame={newerFrame} />);

    await waitFor(() =>
      expect(screen.getByRole("img", { name: /captured stream frame/i })).toHaveAttribute(
        "src",
        "/api/video/capture/frame?frameRef=s3%3A%2F%2Fstreamsense-frames%2Ftest.png",
      ),
    );
  });

  it("shows the ml-engine problem detail when segmentation fails", async () => {
    server.use(restProblem("post", "/ml/segment", 503, "segmentation model is not loaded"));
    const user = userEvent.setup();

    render(<SegmentationPreview frame={frame} />);
    await user.click(screen.getByRole("button", { name: "Run SAM" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("segmentation model is not loaded");
  });

  it("disables the button until a frame reference is available", async () => {
    const user = userEvent.setup();

    render(<SegmentationPreview />);

    expect(screen.getByRole("button", { name: "Run SAM" })).toBeDisabled();
    await user.type(screen.getByRole("textbox"), "frames/manual.png");
    expect(screen.getByRole("button", { name: "Run SAM" })).toBeEnabled();
  });
});
