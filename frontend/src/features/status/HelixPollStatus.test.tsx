import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { helixStatusPolling } from "../../test/fixtures";
import { restJson, restProblem, server } from "../../test/msw";
import { HelixPollStatus } from "./HelixPollStatus";

describe("HelixPollStatus", () => {
  it("renders the last poll from the status endpoint", async () => {
    server.use(
      restJson("get", "/api/analytics/helix/status", { ...helixStatusPolling, lastPollAt: Date.now() - 40_000 }),
    );

    render(<HelixPollStatus />);

    expect(screen.getByText("Helix: checking")).toBeInTheDocument();
    expect(await screen.findByText(/^Helix: 3 watched, 1 live, 4\ds ago$/)).toBeInTheDocument();
  });

  it("shows the failure with its message as the tooltip", async () => {
    server.use(
      restJson("get", "/api/analytics/helix/status", {
        ...helixStatusPolling,
        lastError: "Twitch answered 503",
        lastErrorAt: Date.now() - 60_000,
        lastAttemptAt: Date.now() - 60_000,
      }),
    );

    render(<HelixPollStatus />);

    const pill = await screen.findByText("Helix: failed 1m ago");
    expect(pill).toHaveAttribute("title", "Twitch answered 503");
  });

  it("reports an unavailable status endpoint with the problem detail as the tooltip", async () => {
    server.use(restProblem("get", "/api/analytics/helix/status", 503, "analytics-service is restarting"));

    render(<HelixPollStatus />);

    const pill = await screen.findByText("Helix: status unavailable");
    expect(pill).toHaveAttribute("title", "analytics-service is restarting");
  });
});
