import { afterEach, describe, expect, it } from "vitest";
import {
  authHeaders,
  authTokenClaims,
  authTokenExpiry,
  authTokenFromHash,
  authTokenFromInput,
  captureAccessLink,
  clearAuthToken,
  isOperatorSession,
  readAuthToken,
  storeAuthToken,
} from "./auth-token";
import { EXPIRES_2030, fakeJwt } from "../test/tokens";

const TOKEN = fakeJwt({ sub: "demo-viewer", exp: EXPIRES_2030 });

function blockedStorage() {
  const refuse = () => {
    throw new Error("storage is blocked");
  };
  return { getItem: refuse, setItem: refuse, removeItem: refuse };
}

describe("auth-token", () => {
  afterEach(() => {
    clearAuthToken(null);
  });

  it("reads the token from an access link and rejects what is not one", () => {
    expect(authTokenFromHash(`#token=${TOKEN}`)).toBe(TOKEN);
    expect(authTokenFromHash(`#from=mail&token=${TOKEN}`)).toBe(TOKEN);
    expect(authTokenFromHash("#token=")).toBeNull();
    expect(authTokenFromHash("#token=not a token")).toBeNull();
    expect(authTokenFromHash("")).toBeNull();

    expect(authTokenFromInput(` https://streamsense.dev/#token=${TOKEN} `)).toBe(TOKEN);
    expect(authTokenFromInput(`${TOKEN}\n`)).toBe(TOKEN);
    expect(authTokenFromInput("https://streamsense.dev/")).toBeNull();
    expect(authTokenFromInput("paste the token here")).toBeNull();
  });

  it("keeps the token for this page load when storage refuses it", () => {
    const storage = blockedStorage();
    expect(readAuthToken(storage)).toBeNull();

    storeAuthToken(TOKEN, storage);
    expect(readAuthToken(storage)).toBe(TOKEN);
    expect(authHeaders(storage, null)).toEqual({ Authorization: `Bearer ${TOKEN}` });

    clearAuthToken(storage);
    expect(readAuthToken(storage)).toBeNull();
    expect(authHeaders(storage, null)).toEqual({});
  });

  it("keeps the token from an access link and takes the fragment out of the address bar", () => {
    const replaced: string[] = [];
    const win = {
      location: { hash: `#token=${TOKEN}`, pathname: "/deals/3", search: "?tab=streams" },
      history: {
        state: { idx: 0 },
        replaceState: (_state: unknown, _title: string, url: string) => replaced.push(url),
      },
    } as unknown as Pick<Window, "location" | "history">;

    expect(captureAccessLink(win)).toBe(TOKEN);
    expect(replaced).toEqual(["/deals/3?tab=streams"]);
    expect(readAuthToken(blockedStorage())).toBe(TOKEN);

    // Without a fragment nothing is touched and whatever the browser already holds stays in effect.
    win.location.hash = "";
    expect(captureAccessLink(win)).toBe(TOKEN);
    expect(replaced).toHaveLength(1);
  });

  it("reads who a Twitch sign-in token is for, and lets access links keep full access", () => {
    const streamer = fakeJwt({ sub: "ninja", login: "ninja", role: "streamer", exp: EXPIRES_2030 });
    expect(authTokenClaims(streamer)).toEqual({
      expiry: new Date(EXPIRES_2030 * 1000),
      login: "ninja",
      role: "streamer",
    });
    expect(authTokenClaims(TOKEN)).toEqual({ expiry: new Date(EXPIRES_2030 * 1000), login: null, role: null });
    expect(authTokenClaims("nope")).toEqual({ expiry: null, login: null, role: null });

    const holding = (token: string | null) => ({ getItem: () => token });
    expect(isOperatorSession(holding(null))).toBe(true);
    expect(isOperatorSession(holding(TOKEN))).toBe(true);
    expect(isOperatorSession(holding(streamer))).toBe(false);
    expect(isOperatorSession(holding(fakeJwt({ sub: "ops", login: "ops", role: "operator" })))).toBe(true);
  });

  it("reads the expiry claim without verifying the token", () => {
    expect(authTokenExpiry(TOKEN)).toEqual(new Date(EXPIRES_2030 * 1000));
    expect(authTokenExpiry(fakeJwt({ sub: "no-expiry" }))).toBeNull();
    expect(authTokenExpiry("not.a-jwt.at-all")).toBeNull();
  });
});
