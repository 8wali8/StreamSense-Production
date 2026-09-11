import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { clearAuthToken } from "../../lib/auth-token";
import { EXPIRES_2030, fakeJwt } from "../../test/tokens";
import { AccessPanel } from "./AccessPanel";

const KEY = "streamsense.authToken";

describe("AccessPanel", () => {
  afterEach(() => {
    clearAuthToken();
  });

  it("shows how long the access link is good for and signs out on request", async () => {
    const reload = vi.fn();
    const user = userEvent.setup();
    window.localStorage.setItem(KEY, fakeJwt({ sub: "demo-viewer", exp: EXPIRES_2030 }));
    render(<AccessPanel reload={reload} />);
    expect(screen.getByText(/^Until .*2030$/)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Sign out" }));
    expect(window.localStorage.getItem(KEY)).toBeNull();
    expect(reload).toHaveBeenCalledOnce();
  });

  it("names a Twitch sign-in by its login and role", () => {
    window.localStorage.setItem(KEY, fakeJwt({ sub: "ninja", login: "ninja", role: "streamer", exp: EXPIRES_2030 }));
    render(<AccessPanel reload={vi.fn()} />);
    expect(screen.getByText("Signed in as")).toBeInTheDocument();
    expect(screen.getByText("@ninja · streamer")).toBeInTheDocument();
  });
});
