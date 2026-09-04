/**
 * Every environment read in the frontend lives here. Vite inlines `VITE_*` variables at build
 * time; leaving them unset means "same origin", which is how the Docker image (nginx proxies
 * /graphql, /api, /ml) and `npm run dev` (Vite proxies the same routes) both work.
 */
const rawBaseUrl = import.meta.env.VITE_API_BASE_URL ?? "";

export const env = {
  /** Origin prefix for REST and GraphQL calls; empty for same-origin. Never ends with a slash. */
  apiBaseUrl: rawBaseUrl.replace(/\/+$/, ""),
  /** localStorage key holding the bearer token the gateway expects on HTTP and WebSocket. */
  authTokenStorageKey: "streamsense.authToken",
} as const;

type BrowserLocation = Pick<Location, "protocol" | "host">;

export function graphqlHttpUrl(): string {
  return `${env.apiBaseUrl}/graphql`;
}

export function graphqlWsUrl(location: BrowserLocation = window.location): string {
  if (env.apiBaseUrl) {
    return `${env.apiBaseUrl.replace(/^http/, "ws")}/graphql`;
  }
  const wsProtocol = location.protocol === "https:" ? "wss" : "ws";
  return `${wsProtocol}://${location.host}/graphql`;
}
