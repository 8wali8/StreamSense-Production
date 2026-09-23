# catalog/01-sponsor-catalog

What relevance scoring knows about a sponsor lived in one config list, matched by exact name, copied into a channel's profile the moment a deal pointed the channel at it. The owner's first live deal, spelled "redbull", missed the "Red Bull" entry and so had no aliases and no terms. Based on `main` at `9d50ee3` (`logo/01-deal-logo`); the design was agreed on 2026-09-22 after the logo work settled where logos live (on the deal, uploaded by the streamer), so the catalog is text only.

## What changed

**sentiment-service.**
- `V6__sponsor_catalog.sql`, `persistence/SponsorCatalogEntity` and its repository: one row per sponsor, keyed by the lower-cased name, with the canonical name, aliases, semantic terms (one per line, as in the profiles table), an optional minimum score, and the update time.
- `service/SponsorCatalogService`: the entries mirrored in memory; `find` reaches an entry by its name or by any alias, case aside; `upsert` trims and de-duplicates terms; `delete`. At start-up the stored rows load first and `streamsense.sentiment.relevance.sponsors` only fill in names the table does not hold, so an operator's edit survives a restart and a config change never overwrites it.
- `SponsorRelevanceProfileService` borrows a profile's aliases and terms from the catalog instead of the config list. A profile keeps the sponsor's name as the deal spelled it, because the reports match mentions by that name; only the terms come from the entry. `updateCatalogEntry` replaces an entry and refreshes every profile that reaches it: each gains the entry's terms and keeps its own, so a term removed from the catalog is removed from a channel by hand. At start-up every stored profile is merged with its entry the same way, so a profile written before the catalog existed (the owner's "redbull") catches up on the first deploy.
- Routes on `SentimentHistoryController`: `GET /api/sentiment/relevance/catalog`, `PUT` (the whole entry, keyed by name), `DELETE /api/sentiment/relevance/catalog/{name}` (204, 404). Operators only, because the gateway already refuses a streamer anything but a read on `/api/sentiment/**`.

**analytics-service.**
- `GET /api/analytics/deals/sponsors`: every sponsor deals name, grouped without regard to case (`sponsor`, `deals`, `channels`, `latestStartsAt`), most recently started first. `model/SponsorUsage`, one grouped query in `DealRepository`, through `DealService`.
- `ChannelScopeFilter` refuses it to a streamer, as it does the Helix status: `OPERATOR_ONLY` is a set now.

**Console.**
- `api/sentiment.ts`: `listSponsorCatalog`, `saveSponsorCatalogEntry`, `removeSponsorCatalogEntry`; `api/analytics.ts`: `listSponsorUsage`.
- `features/ops/SponsorCatalog.tsx`, on the Operations page under the channel control and the per-channel profile editor: the entries with their aliases, term count, minimum score, and how many deals reach them; "Named by deals, not in the catalog", the sponsors no entry reaches, each with an Add that starts the form with that name; the form (name, minimum score, aliases, terms) for a new entry or, from a row's Edit, an existing one; Remove. `catalog.ts` holds the pure lookup and the queue rule.
- The per-channel profile editor stays for a channel that needs an exception on top of the catalog.

**Docs.** `docs/contracts/deals.md` (the catalog, its routes, the refresh rule, and the sponsors list), `CLAUDE.md` (the Operations page and the scope filter).

## Deliberately left alone

- **Canonical spelling on the deal.** A deal named "redbull" stays "redbull"; the detection plan's `dealId` on events (logo/02) is the real fix for names drifting, and a suggestion in the form waits until the logo line is quiet in `DealForm`.
- **A term removed from the catalog** stays on the channels that already borrowed it. The alternative, recomputing each profile as catalog terms plus the channel's own, would need the profile to remember which terms were its own; a small later change if the operator asks.
- **Logos in the catalog.** Decided against on 2026-09-22: the detector matches the file the streamer renders, which lives on the deal.

## Verification

| Check | Command | Result |
|---|---|---|
| sentiment-service | `mvn -pl sentiment-service -Dmaven.gitcommitid.skip=true spotless:apply verify` in `maven:3.9-eclipse-temurin-21` | build success; 44 tests, 0 failures (6 new in `SponsorCatalogServiceTest` and `SponsorRelevanceProfileServiceTest`); coverage floor held |
| analytics-service | the same, `-pl analytics-service clean spotless:check verify`, after merging `main` at `f152ef6` (#81, the logo migration renumbered to V10; on `9d50ee3` two V9 migrations failed every Spring context) | build success; 70 tests, 0 failures; Spotless and the coverage floor hold. A first run without `clean` failed the same way as `9d50ee3`: `target/classes` still held the old V9 file, which Maven never deletes on its own |
| Frontend gate | `codegen:check`, `lint`, `format:check`, `test:coverage`, `build` | all pass; 41 files, 168 tests (4 new: `catalog.test.ts`, `SponsorCatalog.test.tsx`); statements 90.01 %, branches 85.38 % |

## What to check by hand

1. After the deploy, Operations: the catalog lists Nike, Prime, Razer, and Red Bull from config, and "Named by deals, not in the catalog" is empty, because the owner's "redbull" reaches Red Bull through its alias.
2. `GET /api/sentiment/relevance/sponsors/8wali8` (from the signed-in tab) shows Red Bull's aliases and terms on the owner's "redbull" profile: it caught up at start-up.
3. Edit Red Bull in the catalog and add a term: the owner's profile gains it on its next read.
4. View as a streamer: the Operations page is not there; `GET /api/analytics/deals/sponsors` with a streamer token is 403 `operator_required`.
