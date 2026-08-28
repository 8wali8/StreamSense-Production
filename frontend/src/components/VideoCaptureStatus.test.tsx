import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { videoStatusCapturing } from "../test/fixtures";
import { HttpResponse, http, restJson, server } from "../test/msw";
import { VideoCaptureStatus } from "./VideoCaptureStatus";

describe("VideoCaptureStatus", () => {
  it("shows capturing state", async () => {
    server.use(restJson("get", "/api/video/capture/status", videoStatusCapturing));

    render(<VideoCaptureStatus />);

    expect(await screen.findByText("Video: capturing @testchannel")).toBeInTheDocument();
  });

  it("shows offline state and the last channel error as the tooltip", async () => {
    server.use(
      restJson("get", "/api/video/capture/status", {
        ...videoStatusCapturing,
        state: "IDLE_OFFLINE",
        channelStatuses: [
          {
            channel: "testchannel",
            state: "IDLE_OFFLINE",
            lastError: "stream is offline",
            lastTranscriptPreview: null,
          },
        ],
      }),
    );

    render(<VideoCaptureStatus />);

    expect(await screen.findByText("Video: stream offline")).toHaveAttribute("title", "stream is offline");
  });

  it("shows disabled state", async () => {
    server.use(
      restJson("get", "/api/video/capture/status", {
        ...videoStatusCapturing,
        enabled: false,
        state: "DISABLED",
        channels: ["disabled"],
      }),
    );

    render(<VideoCaptureStatus />);

    expect(await screen.findByText("Video: disabled")).toBeInTheDocument();
  });

  it("shows unavailable when the request fails", async () => {
    server.use(http.get("*/api/video/capture/status", () => HttpResponse.error()));

    render(<VideoCaptureStatus />);

    expect(await screen.findByText("Video: status unavailable")).toBeInTheDocument();
  });
});
