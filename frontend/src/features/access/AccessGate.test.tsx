import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it } from "vitest";
import { clearAuthToken } from "../../lib/auth-token";
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
  afterEach(() => {
    clearAuthToken();
    window.sessionStorage.clear();
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
