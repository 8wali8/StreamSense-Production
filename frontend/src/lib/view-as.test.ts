import { afterEach, describe, expect, it } from "vitest";
import { readStoredSelection } from "../features/streamer/useStreamerSelection";
import { EXPIRES_2030, fakeJwt } from "../test/tokens";
import { clearAuthToken, storeAuthToken } from "./auth-token";
import { isOperatorView, readViewAs, startViewAs, stopViewAs, VIEW_AS_STORAGE_KEY } from "./view-as";

const OPERATOR = fakeJwt({ sub: "8wali8", login: "8wali8", role: "operator", exp: EXPIRES_2030 });
const STREAMER = fakeJwt({ sub: "ninja", login: "ninja", role: "streamer", exp: EXPIRES_2030 });

function memory(initial: string | null = null) {
  let value = initial;
  return {
    getItem: () => value,
    setItem: (_key: string, next: string) => {
      value = next;
    },
    removeItem: () => {
      value = null;
    },
  };
}

function blocked() {
  const refuse = () => {
    throw new Error("storage is blocked");
  };
  return { getItem: refuse, setItem: refuse, removeItem: refuse };
}

describe("view as", () => {
  afterEach(() => {
    clearAuthToken();
    stopViewAs();
    window.localStorage.clear();
  });

  it("keeps a normalised login for the tab and forgets it on request", () => {
    const storage = memory();
    expect(readViewAs(storage)).toBeNull();
    expect(startViewAs("  @Ninja ", storage)).toBe("ninja");
    expect(readViewAs(storage)).toBe("ninja");
    expect(startViewAs("   ", storage)).toBeNull();
    expect(readViewAs(storage)).toBe("ninja");
    stopViewAs(storage);
    expect(readViewAs(storage)).toBeNull();
  });

  it("does nothing when storage is blocked, rather than half-switching", () => {
    expect(startViewAs("ninja", blocked())).toBeNull();
    expect(readViewAs(blocked())).toBeNull();
    stopViewAs(blocked());
  });

  it("shows the operator controls only to an operator who is not viewing as someone", () => {
    const holding = (token: string | null) => ({ getItem: () => token });
    expect(isOperatorView(holding(OPERATOR), memory())).toBe(true);
    expect(isOperatorView(holding(OPERATOR), memory("ninja"))).toBe(false);
    expect(isOperatorView(holding(STREAMER), memory())).toBe(false);
    // No token at all: auth is off locally, everything shows.
    expect(isOperatorView(holding(null), memory())).toBe(true);
  });

  it("pins the console to the viewed channel ahead of the browser's last selection", () => {
    storeAuthToken(OPERATOR);
    window.localStorage.setItem("streamsense.selection", JSON.stringify({ streamer: "xqc", sponsor: "Red Bull" }));
    expect(readStoredSelection()).toEqual({ streamer: "xqc", sponsor: "Red Bull" });
    window.sessionStorage.setItem(VIEW_AS_STORAGE_KEY, "ninja");
    expect(readStoredSelection()).toEqual({ streamer: "ninja", sponsor: "Red Bull" });
  });
});
