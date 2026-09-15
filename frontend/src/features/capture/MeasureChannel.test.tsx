import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { renderWithApollo } from "../../test/apollo";
import { HttpResponse, delay, restJson, restProblem, restResolver, server } from "../../test/msw";
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

  it("stays loading until both reads have answered", async () => {
    // The chat read answers, the capture read does not: the panel must not claim the channel is
    // unmeasured (and offer Start) while it still has no idea whether video is capturing.
    let chatAnswered = false;
    server.use(
      restResolver("get", CHAT, () => {
        chatAnswered = true;
        return HttpResponse.json(chatBody(false));
      }),
      restResolver("get", CAPTURE, async () => {
        await delay("infinite");
        return HttpResponse.json(captureBody("CAPTURING"));
      }),
    );
    renderWithApollo(<MeasureChannel channel="ninja" />);

    await waitFor(() => expect(chatAnswered).toBe(true));
    expect(screen.getByText(/Checking whether @ninja is being measured/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Start measuring" })).not.toBeInTheDocument();
    expect(screen.queryByText(/not being measured/)).not.toBeInTheDocument();
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
