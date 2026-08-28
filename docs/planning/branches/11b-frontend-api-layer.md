# hardening/11b-frontend-api-layer

Priority 10 (second frontend branch) from `docs/planning/production-hardening.md`: one place that talks HTTP, typed per-endpoint functions, and a dev server that works against the Compose backend. Stacked on `hardening/11a-frontend-codegen`.

## What was wrong

- Six bare `fetch` calls spread over `App.tsx`, `SegmentationPreview`, `TwitchIngestionStatus`, and `VideoCaptureStatus`, each with its own header building, `response.ok` check, error string, and JSON cast. None sent the bearer token that the GraphQL links send (so with `STREAMSENSE_GATEWAY_AUTH_ENABLED=true` every REST call from the console failed), none had a timeout, and none read the RFC 9457 problem body the services now return (branch 09), so the UI could only say "returned 409".
- The two status pills and the transcript fallback each hand-rolled the same "load now, poll every 10 s, cancel on unmount" effect.
- `npm run dev` had no proxy, so the only way to see the console against real data was the Docker image. The frontend README was still the Vite template.

## What changed

- **`src/config/env.ts`** is the only reader of `import.meta.env`: `apiBaseUrl` (`VITE_API_BASE_URL`, default same-origin, typed in `src/vite-env.d.ts`), the auth token storage key, and `graphqlHttpUrl()` / `graphqlWsUrl()` derived from it. `apollo/client.ts` uses them; its exported helpers keep their signatures and tests.
- **`src/lib/api-client.ts`**: `apiRequest` / `apiFetch<T>` / `apiSend` / `apiUrl`. Every call gets the base URL, `Accept`, `Content-Type` when there is a body, the same `Authorization: Bearer` header as GraphQL (`src/lib/auth-token.ts`, shared with the Apollo links), and an `AbortSignal.timeout` (10 s default, 60 s for segmentation). A non-2xx response throws `ApiError` with `status`, `path`, and the parsed `problem` (`detail`, `service`, `correlationId`, `errors[]`), so the message reads `/api/chat/twitch/channels returned 409: Twitch chat ingestion is disabled`. Test doubles without headers still work.
- **`src/api/{chat,video,sentiment,ml}.ts`**: one typed function per endpoint (`getTwitchIngestionStatus`, `switchTwitchChannels`, `getVideoCaptureStatus`, `switchCaptureChannels`, `frameImageUrl`, `getRecentTranscriptSegments`, `updateSponsorProfile`, `segmentFrame`) with the response types that used to live in the components. The transcript REST type is the generated GraphQL selection type, since the REST body is a superset.
- **`src/hooks/usePolledResource`**: load now and every N ms, keyed (by streamer) so a late response for a previous key never shows under the current one, keeps the last good data through a failure, `refresh()` on demand. Used by both status pills and the transcript fallback in `App.tsx`, which lost its `useEffect` and hand-rolled state.
- **Vite dev proxy** (`vite.config.ts`): port 3000, `/graphql` (with WebSocket upgrade) and `/api` to `VITE_DEV_API_TARGET` (default `http://localhost:8080`), `/ml` to `VITE_DEV_ML_TARGET` (default `http://localhost:8000`), read through `loadEnv` so `.env.local` works. `frontend/.env.example` documents the three variables; `frontend/README.md` is now a real README.
- **`frontend/.gitattributes`** pins `src/graphql/generated.ts` to LF. With `core.autocrlf=true` the checkout of branch 11a's file came back CRLF and `npm run codegen:check` reported it stale on Windows even though nothing changed; the attribute makes the working copy byte-identical to what codegen writes.
- **CLAUDE.md** describes the layer and the rule: components never call `fetch` or read `import.meta.env`.

## Deliberately left alone

- No data-fetching library (TanStack Query) for the REST reads. Four polled resources do not justify a second cache next to Apollo's; the hook is 50 lines and tested.
- `App.tsx` is otherwise untouched (still 800 lines); branch 11c splits it.
- Existing component tests still stub `globalThis.fetch`; they pass unchanged because the client uses global `fetch`. Branch 11d moves them to MSW.
- The gateway's REST proxy routes are not changed; if auth is enabled, the REST calls now carry the token, which they never did before.

## Verification

| Check | Command | Result |
|---|---|---|
| Lint | `npm run lint` | clean |
| Type-check and bundle | `npm run build` | OK |
| Tests | `npx vitest run` | 15 files, 49 tests passed (9 new: `api-client.test.ts` ×5, `usePolledResource.test.tsx` ×4) |
| Generated types current | `npm run codegen:check` | exit 0 after the `.gitattributes` fix (exit 1 before it, on a CRLF checkout) |
| Dev proxy routes | two stub JSON servers on 8080 and 8000, `npx vite --port 3000`, `curl localhost:3000/api/chat/twitch/status` and `/ml/live` | each answered by the right upstream (`"port": 8080` / `"port": 8000`); `/` serves `index.html` |
| Docker image | `docker build frontend/` | builds |

## Manual checks for the reviewer

1. `make up`, then in `frontend/` run `npm run dev` and open `http://localhost:3000`: the console loads data and subscriptions stream over the proxied WebSocket, with no CORS errors in the browser console.
2. `docker compose stop chat-service`, then change the streamer in the console: the status line still reports one failed runtime update, and the browser console shows an `ApiError` whose message ends with the gateway's status.
3. With `STREAMSENSE_GATEWAY_AUTH_ENABLED=true` and a token in `localStorage["streamsense.authToken"]`, the Network tab shows `Authorization` on `/api/...` requests as well as `/graphql`.
4. On Windows: `git checkout hardening/11b-frontend-api-layer`, `npm run codegen:check` exits 0.

## Follow-ups (branches 11c, 11d)

- Split `App.tsx` into feature folders and a `useLiveFeed` hook; surface `ApiError.problem.detail` and GraphQL `extensions.code` per panel.
- MSW handlers and a real `ApolloProvider` in tests; type-aware ESLint; Prettier.
