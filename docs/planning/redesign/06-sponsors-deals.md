# 06-sponsors-deals: durable sponsor profiles and deals

Branch `redesign/06-sponsors-deals`, cut from `redesign/main` after 05. Serves S5 (how is this deal going) and the deal half of S2's value section (the fee and the rates a session is priced at). Contract in `docs/contracts/deals.md`.

## What changed

- **Sponsor profiles persist** (sentiment-service). `V5__sponsor_relevance_profiles.sql` stores one profile per streamer; the service loads stored rows at start-up and only then applies the config-repo seeds to channels that have none, so an operator's or a deal's choice survives a restart. `GET /api/sentiment/relevance/sponsors` and `/{streamer}` read them back; the update request lost `campaignGoal` and ignores unknown fields.
- **Deals** (analytics-service). `V4__deals.sql` holds the deal with its terms: dates, promised streams, fee and currency, CPM and host-read rate, tracked link, chat command, channel point reward. `POST /api/analytics/deals`, `GET /api/analytics/deals[?streamer]`, `/{id}`, and `/{id}/summary` (`DealController`, `DealService`, `DealRepository`). The summary lists every session that started inside the deal's dates with its full report priced at the deal's terms, and totals over them. Creating a deal whose dates cover now points relevance at its sponsor through `SponsorRelevancePointer`, a bounded `RestClient` to sentiment-service that exists only when `streamsense.services.sentiment-service.base-url` is set, best effort. The Helix poller also watches every streamer with an active deal.
- **Session reports know their deal.** `SessionSummaryService` resolves the newest deal on the channel covering the session's start (for the named sponsor when one is requested), fills the sponsor, rates, command, and link host the request left unspecified from it, and returns `dealId`.
- **Gateway**: `deals`, `deal`, and `dealSummary` queries; `Deal`, `DealTotals`, `DealSummary` types; `SessionSummary.dealId`. Writes stay on the REST route.
- **Frontend** (`features/deals/`): `/deals/:dealId` (`DealPage.tsx`: header with dates, progress against promised streams, command and link host, the active pill and the disabled Share control; four tiles with media value against the fee; the per-stream trend in `DealTrend.tsx` over `trend.ts`; the stream list, each row a link to that session's report for the deal's sponsor). The home's placeholder became `DealsPanel.tsx`: the channel's deals as links and the "New deal" form (`NewDealForm.tsx`, posting to the REST route and refetching). The session report links to its deal. The ops sponsor editor loads the stored profile for the channel and shows it before saving.
- **Config and cluster**: `streamsense.services.sentiment-service.*` in `analytics-service.yml`; network policy egress from analytics-service to sentiment-service on 8083 and the matching ingress rule.
- **Tests** (minimal): the profile service keeps a stored profile over a seed; `DealsTest` creates a deal over REST, checks the session inside it inherits the command and CPM and carries the deal id, rolls it up in the summary, and rejects an end before the start and a relative link; gateway `DealsQueryTest` (list and summary mapping, null for unknown or malformed ids); frontend `deal-format`, `trend`, the deal page, and creating a deal from the home page.

## Verification

Run on 2026-09-09 with the scratchpad JDK 21 and Maven 3.9.9 (Docker not running, so the Testcontainers tests skip as they do in CI without Docker).

| Check | Result |
|---|---|
| `mvn verify` sentiment-service | all tests pass, Spotless and JaCoCo floor hold |
| `mvn verify` analytics-service | 32 tests pass, line coverage 86% (floor 0.83) |
| `mvn verify` api-gateway | 97 tests pass, 5 skipped (Redis Testcontainer) |
| `npm run test:coverage` | 24 files, 87 tests passed. Statements and lines 88.7%, branches 80.3%, functions 87.7% (floors 85/80/80/85) |
| `npm run lint`, `format:check`, `codegen:check`, `npm run build` | clean |
| `tools/k8s/check_network_policies.py` | OK, 65 edges allowed by 20 policies |
| `kubectl kustomize .` | builds (with the example secrets env copied into place) |

## Manual checks for the reviewer

1. `make up`, open `http://localhost:3000/`, click "New deal": sponsor Red Bull, start today, fee 2500, command `redbull`, link `https://www.redbull.com/f1`. The deal appears in the list; `GET localhost:8083/api/sentiment/relevance/sponsors/redbull-testing` now names Red Bull.
2. Restart sentiment-service (`docker compose restart sentiment-service`). The profile endpoint still returns the same profile.
3. Run the replay alias for a few minutes and open the deal: the stream shows up with its on-screen time; its report shows "Part of the Red Bull deal" and the value page prices at the deal's CPM; `!redbull` uses count in the number strip.
4. `/ops` shows "Stored profile: Red Bull, N aliases, M terms" in the sponsor editor.

## Left for later branches

- Editing and ending a deal (there is create, list, get, summary only).
- Media value against the fee shows only to whoever is signed in; 07 strips the fee for share links.
- Channel point redemptions are stored on the deal but nothing counts them yet (needs Twitch EventSub).
- Pointing relevance at a deal that starts in the future when its start arrives (today only a deal that covers now re-points the channel).
