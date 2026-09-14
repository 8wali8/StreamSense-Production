import { normalizeStreamerHandle } from "../features/streamer/streamer";
import { isOperatorSession, type TokenStorage } from "./auth-token";

/**
 * "View as": an operator looks at the console the way a streamer with a given login sees it, pinned to
 * that channel, without the operations page. Kept per tab in session storage, so the operator's other
 * tabs are unaffected and a reload keeps the view. The token does not change: the gateway still sees
 * the operator, so what renders is the streamer's shape of the console over the operator's access.
 */

export const VIEW_AS_STORAGE_KEY = "streamsense.viewAs";

export type ViewAsStorage = Pick<Storage, "getItem" | "setItem" | "removeItem">;

function defaultStorage(): ViewAsStorage | null {
  try {
    return typeof window === "undefined" ? null : window.sessionStorage;
  } catch {
    return null;
  }
}

/** The login being viewed as, or null when the operator is looking at the console as themselves. */
export function readViewAs(storage: ViewAsStorage | null = defaultStorage()): string | null {
  try {
    const login = storage?.getItem(VIEW_AS_STORAGE_KEY) ?? "";
    return login === "" ? null : login;
  } catch {
    return null;
  }
}

/** Start viewing as the given login (an `@` and case are dropped). Returns the login kept, or null for a blank. */
export function startViewAs(login: string, storage: ViewAsStorage | null = defaultStorage()): string | null {
  const handle = normalizeStreamerHandle(login);
  if (!handle) return null;
  try {
    storage?.setItem(VIEW_AS_STORAGE_KEY, handle);
  } catch {
    // Blocked storage: the view cannot be kept across the reload that follows, so nothing changes.
    return null;
  }
  return handle;
}

export function stopViewAs(storage: ViewAsStorage | null = defaultStorage()): void {
  try {
    storage?.removeItem(VIEW_AS_STORAGE_KEY);
  } catch {
    // Nothing was kept there.
  }
}

/**
 * Whether the console should show the operator's controls: the token is an operator's and the operator
 * is not viewing as a streamer.
 */
export function isOperatorView(
  tokenStorage?: TokenStorage | null,
  storage: ViewAsStorage | null = defaultStorage(),
): boolean {
  const operator = tokenStorage === undefined ? isOperatorSession() : isOperatorSession(tokenStorage);
  return operator && readViewAs(storage) === null;
}
