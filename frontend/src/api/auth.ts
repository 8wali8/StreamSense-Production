import { apiFetch } from "../lib/api-client";

/** GET /auth/providers: which sign-in methods this deployment offers. Public; no token needed. */
export type AuthProviders = { twitch: boolean };

export function fetchAuthProviders(): Promise<AuthProviders> {
  return apiFetch<AuthProviders>("/auth/providers");
}

/** Where the gateway starts a Twitch sign-in; the browser comes back to `returnPath` signed in. */
export function twitchSignInUrl(returnPath: string): string {
  return `/auth/twitch/login?return=${encodeURIComponent(returnPath)}`;
}
