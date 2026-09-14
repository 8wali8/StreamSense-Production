import { describe, expect, it } from "vitest";
import { describeMeasurement } from "./measurement";

const chat = (joined: boolean, enabled = true) => ({ channel: "ninja", joined, enabled, state: "CONNECTED" });
const capture = (state: string) => ({
  channel: "ninja",
  state,
  captureSessionId: null,
  lastFrameAt: null,
  lastError: null,
});

describe("describeMeasurement", () => {
  it("is off before anything has answered", () => {
    expect(describeMeasurement(null, null)).toMatchObject({ measuring: false, unavailable: false });
  });

  it("is on when both halves are running", () => {
    const result = describeMeasurement(chat(true), capture("CAPTURING"));

    expect(result.measuring).toBe(true);
    expect(result.detail).toContain("Chat and video");
  });

  it("says which half is missing rather than rounding to on", () => {
    expect(describeMeasurement(chat(true), capture("STOPPED")).detail).toContain("no on-screen exposure");
    expect(describeMeasurement(chat(false), capture("CAPTURING")).detail).toContain("no chat mentions");
  });

  it("reports a channel nothing is running for", () => {
    const result = describeMeasurement(chat(false), capture("STOPPED"));

    expect(result.measuring).toBe(false);
    expect(result.unavailable).toBe(false);
    expect(result.detail).toContain("not being measured");
  });

  it("marks the control unavailable when the deployment has both switched off", () => {
    const result = describeMeasurement(chat(false, false), capture("DISABLED"));

    expect(result.unavailable).toBe(true);
    expect(result.detail).toContain("operator");
  });
});
