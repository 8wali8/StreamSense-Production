import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { renderWithApollo } from "../../test/apollo";
import { restJson, restProblem, server } from "../../test/msw";
import { MeasureChannel } from "./MeasureChannel";

const CHAT = "/api/chat/twitch/channels/ninja";
const CAPTURE = "/api/video/capture/channels/ninja";

const chatBody = (joined: boolean) => ({ channel: "ninja", joined, enabled: true, state: "CONNECTED" });
const captureBody = (state: string) => ({
  channel: "ninja",
  state,
  captureSessionId: null,
  lastFrameAt: null,
  lastError: null,
});

describe("MeasureChannel", () => {
  it("starts measurement of the channel and reports it", async () => {
    server.use(
      restJson("get", CHAT, chatBody(false)),
      restJson("get", CAPTURE, captureBody("STOPPED")),
      restJson("put", CHAT, chatBody(true)),
      restJson("put", CAPTURE, captureBody("CAPTURING")),
    );
    renderWithApollo(<MeasureChannel channel="ninja" />);
    expect(await screen.findByText(/not being measured/)).toBeInTheDocument();

    // The reads answer with the started state from here on, as they would after the two writes.
    server.use(restJson("get", CHAT, chatBody(true)), restJson("get", CAPTURE, captureBody("CAPTURING")));
    await userEvent.click(screen.getByRole("button", { name: "Start measuring" }));

    expect(await screen.findByText("Chat and video are being measured.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Stop measuring" })).toBeInTheDocument();
  });

  it("names the half that refused instead of claiming the channel is measured", async () => {
    server.use(
      restJson("get", CHAT, chatBody(false)),
      restJson("get", CAPTURE, captureBody("STOPPED")),
      restJson("put", CHAT, chatBody(true)),
      restProblem("put", CAPTURE, 409, "Twitch video capture is already measuring 10 channels"),
    );
    renderWithApollo(<MeasureChannel channel="ninja" />);
    await screen.findByRole("button", { name: "Start measuring" });

    server.use(restJson("get", CHAT, chatBody(true)));
    await userEvent.click(screen.getByRole("button", { name: "Start measuring" }));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent(/video capture/);
    expect(alert).toHaveTextContent(/already measuring 10 channels/);
  });

  it("cannot be used when the deployment has ingest switched off", async () => {
    server.use(
      restJson("get", CHAT, { channel: "ninja", joined: false, enabled: false, state: "DISABLED" }),
      restJson("get", CAPTURE, captureBody("DISABLED")),
    );
    renderWithApollo(<MeasureChannel channel="ninja" />);

    await waitFor(() => expect(screen.getByRole("button", { name: "Start measuring" })).toBeDisabled());
    expect(screen.getByText(/An operator turns them on/)).toBeInTheDocument();
  });
});
