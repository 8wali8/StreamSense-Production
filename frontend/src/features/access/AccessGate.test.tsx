import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { clearAuthToken } from "../../lib/auth-token";
import { restJson, server } from "../../test/msw";
import { EXPIRED_2020, EXPIRES_2030, fakeJwt } from "../../test/tokens";
import { AccessGate } from "./AccessGate";

const KEY = "streamsense.authToken";
const TOKEN = fakeJwt({ sub: "demo-viewer", exp: EXPIRES_2030 });

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

  it("shows the sign-in page until a pasted access link is accepted, then the console", async () => {
    const user = userEvent.setup();
    renderGate();
    expect(screen.getByText("Paste your access link or token below.")).toBeInTheDocument();
    expect(screen.queryByText("the console")).not.toBeInTheDocument();

    const field = screen.getByLabelText("Access link or token");
    await user.click(field);
    await user.paste("https://streamsense.dev/");
    await user.click(screen.getByRole("button", { name: "Open the console" }));
    expect(screen.getByRole("alert")).toHaveTextContent("not an access link");

    await user.clear(field);
    await user.click(field);
    await user.paste(`https://streamsense.dev/#token=${TOKEN}`);
    await user.click(screen.getByRole("button", { name: "Open the console" }));
    expect(screen.getByText("the console")).toBeInTheDocument();
    expect(window.localStorage.getItem(KEY)).toBe(TOKEN);
  });

  it("offers Twitch sign-in when the gateway has it on, returning to the current page", async () => {
    server.use(restJson("get", "/auth/providers", { twitch: true }));
    window.history.replaceState(null, "", "/deals/3?tab=streams");
    renderGate();
    const button = await screen.findByRole("link", { name: /sign in with twitch/i });
    expect(button).toHaveAttribute("href", "/auth/twitch/login?return=%2Fdeals%2F3%3Ftab%3Dstreams");
  });

  it("explains a Twitch sign-in that came back with a reason, and drops it from the address", () => {
    window.history.replaceState(null, "", "/?signin=denied&x=1");
    renderGate();
    expect(screen.getByRole("alert")).toHaveTextContent("Twitch sign-in was cancelled");
    expect(window.location.search).toBe("?x=1");
  });

  it("treats a token that has run out as a sign-in with the expiry explained", () => {
    window.localStorage.setItem(KEY, fakeJwt({ sub: "demo-viewer", exp: EXPIRED_2020 }));
    renderGate();
    expect(screen.getByText(/Your access link has expired/)).toBeInTheDocument();
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
