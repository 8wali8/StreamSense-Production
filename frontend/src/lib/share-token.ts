/**
 * Read-only share links. A link carries `?share=<token>`; the token is kept in sessionStorage for
 * the tab so every request in it sends the share header, and the app renders the shared view.
 */

export const SHARE_STORAGE_KEY = "streamsense.shareToken";
export const SHARE_HEADER = "X-StreamSense-Share";

export type ShareStorage = Pick<Storage, "getItem" | "setItem">;

/** The token from this page load's URL, kept when session storage is blocked or refuses the write. */
let tokenInMemory: string | null = null;

function defaultStorage(): ShareStorage | null {
  try {
    return typeof window === "undefined" ? null : window.sessionStorage;
  } catch {
    return null;
  }
}

/** The token from a page URL's `share` parameter, or null. */
export function shareTokenFromSearch(search: string): string | null {
  const token = new URLSearchParams(search).get("share")?.trim() ?? "";
  return token === "" ? null : token;
}

/** Remember the token from the URL for this tab; returns the token in effect afterwards. */
export function captureShareToken(search: string, storage: ShareStorage | null = defaultStorage()): string | null {
  const fromUrl = shareTokenFromSearch(search);
  if (fromUrl) {
    tokenInMemory = fromUrl;
    try {
      storage?.setItem(SHARE_STORAGE_KEY, fromUrl);
    } catch {
      // Storage can be blocked; the in-memory copy serves this page load.
    }
  }
  return fromUrl ?? readShareToken(storage);
}

export function readShareToken(storage: ShareStorage | null = defaultStorage()): string | null {
  try {
    return storage?.getItem(SHARE_STORAGE_KEY) ?? tokenInMemory;
  } catch {
    return tokenInMemory;
  }
}

/** The share header for a request without a bearer token. */
export function shareHeaders(storage: ShareStorage | null = defaultStorage()): Record<string, string> {
  const token = readShareToken(storage);
  return token ? { [SHARE_HEADER]: token } : {};
}

/** The URL a streamer sends: the deal page with the token. */
export function shareUrl(dealId: string, token: string, origin: string = window.location.origin): string {
  return `${origin}/deals/${encodeURIComponent(dealId)}?share=${encodeURIComponent(token)}`;
}
