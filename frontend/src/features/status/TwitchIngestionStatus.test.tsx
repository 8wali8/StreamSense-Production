import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { twitchStatusConnected } from "../../test/fixtures";
import { restJson, restProblem, server } from "../../test/msw";
import { TwitchIngestionStatus } from "./TwitchIngestionStatus";

describe("TwitchIngestionStatus", () => {
  it("renders disabled state from the status endpoint", async () => {
    server.use(
      restJson("get", "/api/chat/twitch/status", {
        ...twitchStatusConnected,
        enabled: false,
        state: "DISABLED",
        channels: [],
      }),
    );

    render(<TwitchIngestionStatus />);

    expect(screen.getByText("Twitch: checking")).toBeInTheDocument();
    expect(await screen.findByText("Twitch: disabled")).toBeInTheDocument();
  });

  it("renders connected channels", async () => {
    server.use(restJson("get", "/api/chat/twitch/status", twitchStatusConnected));

    render(<TwitchIngestionStatus />);

    expect(await screen.findByText("Twitch: connected @testchannel")).toBeInTheDocument();
  });

  it("reports an unavailable status endpoint with the problem detail as the tooltip", async () => {
    server.use(restProblem("get", "/api/chat/twitch/status", 503, "chat-service is restarting"));

    render(<TwitchIngestionStatus />);

    const pill = await screen.findByText("Twitch: status unavailable");
    expect(pill).toHaveAttribute("title", "chat-service is restarting");
  });
});
