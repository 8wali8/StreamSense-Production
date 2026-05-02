import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { VideoCaptureStatus } from "./VideoCaptureStatus";

describe("VideoCaptureStatus", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("shows disabled state", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue({
      ok: true,
      json: async () => ({ enabled: false, state: "DISABLED", channels: [], lastFrameAt: null }),
    } as Response);

    render(<VideoCaptureStatus />);

    await waitFor(() => expect(screen.getByText("Video: disabled")).toBeInTheDocument());
  });

  it("shows capturing state", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue({
      ok: true,
      json: async () => ({ enabled: true, state: "CAPTURING", channels: ["austincs"], lastFrameAt: 1710000000000 }),
    } as Response);

    render(<VideoCaptureStatus />);

    await waitFor(() => expect(screen.getByText("Video: capturing @austincs")).toBeInTheDocument());
  });

  it("shows unavailable state on request failure", async () => {
    vi.spyOn(globalThis, "fetch").mockRejectedValue(new Error("network down"));

    render(<VideoCaptureStatus />);

    await waitFor(() => expect(screen.getByText("Video: status unavailable")).toBeInTheDocument());
  });
});
