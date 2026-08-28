import { env } from "../config/env";

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

/** The Authorization header for REST calls, GraphQL over HTTP, and the WebSocket connectionParams. */
export function authHeaders(storage: TokenStorage | null = defaultStorage()): Record<string, string> {
  const token = readAuthToken(storage);
  return token ? { Authorization: `Bearer ${token}` } : {};
}
