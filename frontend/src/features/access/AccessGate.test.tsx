import { render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { clearAuthToken } from "../../lib/auth-token";
import { restJson, restProblem, server } from "../../test/msw";
import { EXPIRED_2020, EXPIRES_2030, fakeJwt } from "../../test/tokens";
import { AccessGate } from "./AccessGate";

const KEY = "streamsense.authToken";
const TOKEN = fakeJwt({ sub: "ninja", login: "ninja", role: "streamer", exp: EXPIRES_2030 });

function renderGate() {
  return render(
    <AccessGate>
      <div>the console</div>
    </AccessGate>,
  );
}

describe("AccessGate", () => {
  beforeEach(() => {
    server.use(restJson("get", "/auth/providers", { twitch: false }));
  });

  afterEach(() => {
    clearAuthToken();
    window.sessionStorage.clear();
    window.history.replaceState(null, "", "/");
  });

  it("shows the sign-in page, and says so when Twitch sign-in is off, without a way to paste a token", async () => {
    renderGate();
    expect(screen.queryByText("the console")).not.toBeInTheDocument();
    expect(await screen.findByText("Sign in with Twitch is not switched on for this console.")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /sign in with twitch/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Take a look at the demo" })).toBeInTheDocument();
  });

  it("offers Twitch sign-in when the gateway has it on, returning to the current page", async () => {
    server.use(restJson("get", "/auth/providers", { twitch: true }));
    window.history.replaceState(null, "", "/deals/3?tab=streams");
    renderGate();
    const button = await screen.findByRole("link", { name: /sign in with twitch/i });
    expect(button).toHaveAttribute("href", "/auth/twitch/login?return=%2Fdeals%2F3%3Ftab%3Dstreams");
    expect(screen.queryByText(/not switched on/)).not.toBeInTheDocument();
  });

  it("says when the sign-in service did not answer", async () => {
    server.use(restProblem("get", "/auth/providers", 503, "gateway down"));
    renderGate();
    expect(await screen.findByText(/The sign-in service did not answer/)).toBeInTheDocument();
  });

  it("explains a Twitch sign-in that came back with a reason, and drops it from the address", () => {
    window.history.replaceState(null, "", "/?signin=denied&x=1");
    renderGate();
    expect(screen.getByRole("alert")).toHaveTextContent("Twitch sign-in was cancelled");
    expect(window.location.search).toBe("?x=1");
  });

  it("treats a token that has run out as a sign-in with the expiry explained", () => {
    window.localStorage.setItem(KEY, fakeJwt({ sub: "ninja", login: "ninja", role: "streamer", exp: EXPIRED_2020 }));
    renderGate();
    expect(screen.getByText(/Your sign-in has run out/)).toBeInTheDocument();
    expect(screen.queryByText("the console")).not.toBeInTheDocument();
  });

  it("lets a stored token and a share-link tab straight through", () => {
    window.localStorage.setItem(KEY, TOKEN);
    const { unmount } = renderGate();
    expect(screen.getByText("the console")).toBeInTheDocument();
    unmount();
    clearAuthToken();

    window.sessionStorage.setItem("streamsense.shareToken", "share-1");
    renderGate();
    expect(screen.getByText("the console")).toBeInTheDocument();
  });
});
