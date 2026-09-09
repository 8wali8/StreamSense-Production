import { env } from "../config/env";
import { shareHeaders, type ShareStorage } from "./share-token";

export type TokenStorage = Pick<Storage, "getItem">;

function defaultStorage(): TokenStorage | null {
  try {
    return typeof window === "undefined" ? null : window.localStorage;
  } catch {
    // Some browsers throw on localStorage access (private mode, blocked site data).
    return null;
  }
}

export function readAuthToken(storage: TokenStorage | null = defaultStorage()): string | null {
  return storage?.getItem(env.authTokenStorageKey) ?? null;
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
