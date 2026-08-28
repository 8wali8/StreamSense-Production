import { act, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { usePolledResource } from "./usePolledResource";

function Probe({ load, streamer }: { load: (streamer: string) => Promise<string>; streamer: string }) {
  const resource = usePolledResource(() => load(streamer), 1000, streamer);
  return (
    <div>
      <span data-testid="data">{resource.data ?? "none"}</span>
      <span data-testid="error">{resource.error ?? "none"}</span>
      <button onClick={resource.refresh}>refresh</button>
    </div>
  );
}

describe("usePolledResource", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("loads immediately and again on every interval", async () => {
    const load = vi.fn(async (streamer: string) => `${streamer}-${load.mock.calls.length}`);

    render(<Probe load={load} streamer="test" />);
    await act(async () => {
      await Promise.resolve();
    });
    expect(screen.getByTestId("data")).toHaveTextContent("test-1");

    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000);
    });
    expect(load).toHaveBeenCalledTimes(3);
    expect(screen.getByTestId("data")).toHaveTextContent("test-3");
  });

  it("keeps the last good data through a failure and reports the error", async () => {
    const load = vi.fn<(streamer: string) => Promise<string>>().mockResolvedValueOnce("first").mockRejectedValueOnce(new Error("network down"));

    render(<Probe load={load} streamer="test" />);
    await act(async () => {
      await Promise.resolve();
    });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(1000);
    });

    expect(screen.getByTestId("data")).toHaveTextContent("first");
    expect(screen.getByTestId("error")).toHaveTextContent("network down");
  });

  it("drops data from a previous key when the key changes", async () => {
    const load = vi.fn(async (streamer: string) => `data-for-${streamer}`);

    const { rerender } = render(<Probe load={load} streamer="alpha" />);
    await act(async () => {
      await Promise.resolve();
    });
    expect(screen.getByTestId("data")).toHaveTextContent("data-for-alpha");

    rerender(<Probe load={load} streamer="beta" />);
    expect(screen.getByTestId("data")).toHaveTextContent("none");
    await act(async () => {
      await Promise.resolve();
    });
    expect(screen.getByTestId("data")).toHaveTextContent("data-for-beta");
  });

  it("reloads on demand", async () => {
    const load = vi.fn(async (streamer: string) => `${streamer}-${load.mock.calls.length}`);

    render(<Probe load={load} streamer="test" />);
    await act(async () => {
      await Promise.resolve();
    });
    await act(async () => {
      screen.getByRole("button", { name: "refresh" }).click();
      await Promise.resolve();
    });

    expect(load).toHaveBeenCalledTimes(2);
    expect(screen.getByTestId("data")).toHaveTextContent("test-2");
  });
});
