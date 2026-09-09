# 07-share-links: read-only share links on a deal

Branch `redesign/07-share-links`, cut from `redesign/main` after 06. Serves S6 (what do I send the sponsor). The public domain from the cloud line is not needed after all: the link is built from the page's own origin, so it works wherever the console is served.

## What changed

- **Token on the deal** (analytics-service): `V5__deal_share_tokens.sql` adds a unique `share_token`; `POST /api/analytics/deals/{id}/share` mints 24 random bytes (base64url) once and returns the same token afterwards, `DELETE` revokes it, and `GET /api/analytics/share/{token}` resolves it to the deal for the gateway.
- **Share mode at the gateway**: `GatewayAuthWebFilter` lets a POST to `/graphql` carrying `X-StreamSense-Share` through without a bearer token (REST routes and subscriptions stay closed). `ShareLinkInterceptor` (a `WebGraphQlInterceptor`) parses the document, refuses anything but `deal`, `dealSummary`, `sessionSummary`, and `sponsorMoments` with `SHARE_FORBIDDEN`, resolves the token through analytics-service (`SHARE_TOKEN_INVALID` when unknown or revoked, `SHARE_UNAVAILABLE` when the service cannot be reached), and puts the deal on the GraphQL context. `DealGraphqlController` answers only for that deal and strips `fee` and `shareToken` (`Deal.forSharedView()`); `SessionGraphqlController` forces the deal's sponsor and terms and hides sessions whose `dealId` is not the shared deal, for both the summary and the timeline.
- **Frontend**: `lib/share-token.ts` reads `?share=` from the URL into the tab's session storage and builds the header and the link; `authHeaders` sends the share header when there is no bearer token, so REST, GraphQL over HTTP, and the WebSocket handshake stay in lockstep; `describeError` knows the three share codes. A shared tab renders a shell with no navigation and no channel, routes `/` to a landing note, and does not route `/ops`. The deal page's Share control (`ShareControl.tsx`) mints the link, shows it in a copyable field, and revokes it; it is hidden in a shared tab. Deal selections include `shareToken`.
- **Tests** (minimal): analytics share, resolve, and revoke; gateway `ShareLinkQueryTest` (shared deal without the fee, another deal null with no extra call, a session outside the deal hidden and the caller's sponsor ignored, refused queries and bad tokens with their codes) and the auth filter letting a share request through only on GraphQL; frontend `share-token` helpers and the deal page mint-then-shared-view flow.

## Verification

Run on 2026-09-09 with the scratchpad JDK 21 and Maven 3.9.9.

| Check | Result |
|---|---|
| `mvn verify` analytics-service | 32 tests pass, coverage floor holds |
| `mvn verify` api-gateway | 101 tests pass, 5 skipped (Redis Testcontainer) |
| `npm run test:coverage` | 25 files, 90 tests passed. Statements and lines 88.3%, branches 80.1%, functions 86.4% (floors 85/80/80/85) |
| `npm run lint`, `format:check`, `codegen:check`, `npm run build` | clean |

## Manual checks for the reviewer

1. With auth enabled (`STREAMSENSE_GATEWAY_AUTH_ENABLED=true` and a minted bearer token in local storage), open a deal and click Share. Copy the link.
2. Open the link in a private window. The deal page renders with no navigation, no Share control, and "Media value · estimate" without the fee; the stream rows open their reports and the four detail pages; `/` shows the landing note and `/ops` is not found.
3. In the private window, run `{ deals(streamer: "redbull-testing") { id } }` against `/graphql` with the `X-StreamSense-Share` header: the answer is a `SHARE_FORBIDDEN` error.
4. Click Revoke on the deal page; reload the private window: the deal page reports that the share link is no longer valid.

## Left for later branches

- A share link is one token per deal; per-recipient links and expiry would need a token table.
- Sharing a single session outside a deal is not offered; the deal is the unit sponsors receive.
- The shared view still ships the whole console bundle; a lighter public build is a cloud-line concern.
