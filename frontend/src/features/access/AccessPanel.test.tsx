import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { clearAuthToken } from "../../lib/auth-token";
import { stopViewAs, VIEW_AS_STORAGE_KEY } from "../../lib/view-as";
import { EXPIRES_2030, fakeJwt } from "../../test/tokens";
import { AccessPanel } from "./AccessPanel";

const KEY = "streamsense.authToken";
const OPERATOR = fakeJwt({ sub: "8wali8", login: "8wali8", role: "operator", exp: EXPIRES_2030 });

describe("AccessPanel", () => {
  afterEach(() => {
    clearAuthToken();
    stopViewAs();
  });

  it("shows how long the access link is good for and signs out on request", async () => {
    const reload = vi.fn();
    const user = userEvent.setup();
    window.localStorage.setItem(KEY, fakeJwt({ sub: "demo-viewer", exp: EXPIRES_2030 }));
    render(<AccessPanel reload={reload} />);
    expect(screen.getByText(/^Until .*2030$/)).toBeInTheDocument();
    // An access link is not an operator, so there is nobody to view as.
    expect(screen.queryByLabelText("View as a streamer")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Sign out" }));
    expect(window.localStorage.getItem(KEY)).toBeNull();
    expect(reload).toHaveBeenCalledOnce();
  });

  it("names a Twitch sign-in by its login and role", () => {
    window.localStorage.setItem(KEY, fakeJwt({ sub: "ninja", login: "ninja", role: "streamer", exp: EXPIRES_2030 }));
    render(<AccessPanel reload={vi.fn()} />);
    expect(screen.getByText("Signed in as")).toBeInTheDocument();
    expect(screen.getByText("@ninja · streamer")).toBeInTheDocument();
    expect(screen.queryByLabelText("View as a streamer")).not.toBeInTheDocument();
  });

  it("lets an operator view the console as a streamer, and come back", async () => {
    const openHome = vi.fn();
    const user = userEvent.setup();
    window.localStorage.setItem(KEY, OPERATOR);
    const { unmount } = render(<AccessPanel reload={vi.fn()} openHome={openHome} />);

    const view = screen.getByRole("button", { name: "View" });
    expect(view).toBeDisabled();
    await user.type(screen.getByLabelText("View as a streamer"), "@Ninja{Enter}");
    expect(window.sessionStorage.getItem(VIEW_AS_STORAGE_KEY)).toBe("ninja");
    expect(openHome).toHaveBeenCalledOnce();
    unmount();

    render(<AccessPanel reload={vi.fn()} openHome={openHome} />);
    expect(screen.getByText("Viewing as")).toBeInTheDocument();
    expect(screen.getByText("@ninja · streamer")).toBeInTheDocument();
    expect(screen.getByText("Signed in as @8wali8 · operator")).toBeInTheDocument();
    expect(screen.queryByLabelText("View as a streamer")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Back to my view" }));
    expect(window.sessionStorage.getItem(VIEW_AS_STORAGE_KEY)).toBeNull();
    expect(openHome).toHaveBeenCalledTimes(2);
  });

  it("drops the view along with the token on sign-out", async () => {
    const reload = vi.fn();
    const user = userEvent.setup();
    window.localStorage.setItem(KEY, OPERATOR);
    window.sessionStorage.setItem(VIEW_AS_STORAGE_KEY, "ninja");
    render(<AccessPanel reload={reload} />);
    await user.click(screen.getByRole("button", { name: "Sign out" }));
    expect(window.sessionStorage.getItem(VIEW_AS_STORAGE_KEY)).toBeNull();
    expect(window.localStorage.getItem(KEY)).toBeNull();
    expect(reload).toHaveBeenCalledOnce();
  });
});
