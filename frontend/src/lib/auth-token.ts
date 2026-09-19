import { env } from "../config/env";
import { shareHeaders, type ShareStorage } from "./share-token";

export type TokenStorage = Pick<Storage, "getItem">;
export type WritableTokenStorage = Pick<Storage, "getItem" | "setItem" | "removeItem">;

/**
 * The token that arrived during this page load, kept when local storage is blocked (private mode, site
 * data off) or refuses the write, so a sign-in still works for the tab it came back to.
 */
let tokenInMemory: string | null = null;

function defaultStorage(): WritableTokenStorage | null {
  try {
    return typeof window === "undefined" ? null : window.localStorage;
  } catch {
    // Some browsers throw on localStorage access (private mode, blocked site data).
    return null;
  }
}

export function readAuthToken(storage: TokenStorage | null = defaultStorage()): string | null {
  try {
    return storage?.getItem(env.authTokenStorageKey) ?? tokenInMemory;
  } catch {
    return tokenInMemory;
  }
}

/** Three base64url segments; the gateway verifies the signature, this only rejects obvious mistakes. */
const JWT_SHAPE = /^[\w-]+\.[\w-]+\.[\w-]+$/;

/** The token from a page URL's fragment, `#token=<jwt>`, or null. */
export function authTokenFromHash(hash: string): string | null {
  const token = new URLSearchParams(hash.replace(/^#/, "")).get("token")?.trim() ?? "";
  return JWT_SHAPE.test(token) ? token : null;
}

/** Keep a token for the next loads of this browser, and for this page load when storage refuses it. */
export function storeAuthToken(token: string, storage: WritableTokenStorage | null = defaultStorage()): void {
  tokenInMemory = token;
  try {
    storage?.setItem(env.authTokenStorageKey, token);
  } catch {
    // Blocked storage: the in-memory copy serves this page load.
  }
}

/**
 * A Twitch sign-in comes back with the bearer token in the URL fragment (`#token=<jwt>`), which the
 * browser never sends to the server. Keep it for this browser and take it out of the address bar, so a
 * copied or bookmarked URL does not carry it on. Returns the token in effect afterwards.
 */
export function captureAccessLink(win: Pick<Window, "location" | "history">): string | null {
  const token = authTokenFromHash(win.location.hash);
  if (!token) return readAuthToken();
  storeAuthToken(token);
  win.history.replaceState(win.history.state, "", `${win.location.pathname}${win.location.search}`);
  return token;
}

export function clearAuthToken(storage: WritableTokenStorage | null = defaultStorage()): void {
  tokenInMemory = null;
  try {
    storage?.removeItem(env.authTokenStorageKey);
  } catch {
    // Nothing was stored there to begin with.
  }
}

/** What a token says about its holder. Read, not verified: the gateway checks the signature. */
export type AuthTokenClaims = {
  /** When the token runs out, or null when it carries no expiry. */
  expiry: Date | null;
  /** The Twitch login the token was minted for, or null for a token minted by hand for a script. */
  login: string | null;
  /** "operator" or "streamer"; every token the gateway accepts carries one, so null means the token cannot be read. */
  role: string | null;
};

export function authTokenClaims(token: string): AuthTokenClaims {
  const none: AuthTokenClaims = { expiry: null, login: null, role: null };
  const payload = token.split(".")[1];
  if (!payload) return none;
  try {
    const base64 = payload.replace(/-/g, "+").replace(/_/g, "/");
    const claims = JSON.parse(atob(base64.padEnd(Math.ceil(base64.length / 4) * 4, "="))) as Record<string, unknown>;
    return {
      expiry: typeof claims.exp === "number" ? new Date(claims.exp * 1000) : null,
      login: typeof claims.login === "string" && claims.login ? claims.login : null,
      role: typeof claims.role === "string" && claims.role ? claims.role : null,
    };
  } catch {
    return none;
  }
}

/**
 * The `exp` claim of a token as a date, or null when it has none or cannot be decoded. This only tells
 * the console whether to ask for a new sign-in.
 */
export function authTokenExpiry(token: string): Date | null {
  return authTokenClaims(token).expiry;
}

/** The claims of the token this browser holds, or null when it holds none. */
export function currentAuthClaims(storage: TokenStorage | null = defaultStorage()): AuthTokenClaims | null {
  const token = readAuthToken(storage);
  return token ? authTokenClaims(token) : null;
}

/**
 * Whether the current token may use the operator pages and pipeline controls: a Twitch sign-in on the
 * operators list. No token at all (auth off locally) counts as one; a streamer does not.
 */
export function isOperatorSession(storage: TokenStorage | null = defaultStorage()): boolean {
  const claims = currentAuthClaims(storage);
  return claims === null || claims.role === "operator";
}

/**
 * Whether this session may start and stop measurement of the channel it is looking at: an operator
 * sign-in (or no token at all, locally) or a streamer, who the gateway confines to their own channel.
 */
export function canStartMeasurement(storage: TokenStorage | null = defaultStorage()): boolean {
  const claims = currentAuthClaims(storage);
  return claims === null || claims.role === "operator" || claims.role === "streamer";
}

/** Whether a token is worth sending: present and not past its expiry. The gateway still has the final say. */
export function isUsableToken(token: string | null, now: () => Date = () => new Date()): token is string {
  if (!token) return false;
  const expiry = authTokenExpiry(token);
  return expiry === null || expiry.getTime() > now().getTime();
}

/**
 * The Authorization header for REST calls, GraphQL over HTTP, and the WebSocket connectionParams.
 * Without a bearer token, a share token from the tab's session (a read-only share link) is sent instead.
 */
export function authHeaders(
  storage: TokenStorage | null = defaultStorage(),
  shareStorage?: ShareStorage | null,
): Record<string, string> {
  const token = readAuthToken(storage);
  if (token) return { Authorization: `Bearer ${token}` };
  return shareStorage === undefined ? shareHeaders() : shareHeaders(shareStorage);
}
