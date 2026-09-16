import { describe, expect, it } from "vitest";
import { helixStatusPolling } from "../../test/fixtures";
import { formatAgo, formatHelixStatus, helixTooltip } from "./helix-status";

const NOW = 1710000045000;

describe("formatHelixStatus", () => {
  it("words each state of the poller", () => {
    expect(formatHelixStatus(null, null, NOW)).toBe("Helix: checking");
    expect(formatHelixStatus(null, "analytics-service is restarting", NOW)).toBe("Helix: status unavailable");
    expect(formatHelixStatus({ ...helixStatusPolling, enabled: false }, null, NOW)).toBe("Helix: disabled");
    expect(formatHelixStatus(helixStatusPolling, null, NOW)).toBe("Helix: 3 watched, 1 live, 45s ago");
    expect(formatHelixStatus({ ...helixStatusPolling, lastAttemptAt: null, lastPollAt: null }, null, NOW)).toBe(
      "Helix: waiting for the first poll",
    );
    expect(
      formatHelixStatus(
        {
          ...helixStatusPolling,
          lastPollAt: NOW - 600_000,
          lastAttemptAt: NOW - 180_000,
          lastError: "twitch down",
          lastErrorAt: NOW - 180_000,
        },
        null,
        NOW,
      ),
    ).toBe("Helix: failed 3m ago");
    // An error older than the last good poll is history, not the current state.
    expect(
      formatHelixStatus({ ...helixStatusPolling, lastError: "twitch down", lastErrorAt: NOW - 600_000 }, null, NOW),
    ).toBe("Helix: 3 watched, 1 live, 45s ago");
    expect(formatHelixStatus({ ...helixStatusPolling, pausedUntil: NOW + 30_000 }, null, NOW)).toMatch(
      /^Helix: paused until \d/,
    );
  });

  it("rounds the age to the unit a glance needs", () => {
    expect(formatAgo(NOW - 5_000, NOW)).toBe("5s ago");
    expect(formatAgo(NOW - 150_000, NOW)).toBe("3m ago"); // 2.5 minutes rounds up
    expect(formatAgo(NOW - 7_200_000, NOW)).toBe("2h ago");
    expect(formatAgo(NOW + 5_000, NOW)).toBe("0s ago");
  });
});

describe("helixTooltip", () => {
  it("prefers the endpoint's own problem over a retained poller error", () => {
    const withError = { ...helixStatusPolling, lastError: "twitch down", lastErrorAt: NOW - 60_000 };
    expect(helixTooltip(withError, null)).toBe("twitch down");
    // The endpoint failed after a good answer: the status is retained, the tooltip is the new problem.
    expect(helixTooltip(withError, "analytics-service is restarting")).toBe("analytics-service is restarting");
    expect(helixTooltip(helixStatusPolling, null)).toBeUndefined();
    expect(helixTooltip(null, null)).toBeUndefined();
  });
});
