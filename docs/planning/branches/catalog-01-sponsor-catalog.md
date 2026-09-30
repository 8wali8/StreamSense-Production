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

## After the Codex review

Six findings on the first push, all taken:

- **The catalog's minimum score now reaches the channels.** It was saved and shown but never used: a profile always stored a score, the configured default when nobody chose one, and a refresh copied it back. A profile's stored score is now the channel's own override only; the score in effect is the override, else the entry's, else the default. The profile reads back both (`minScore`, `minScoreOverride`), and the per-channel editor fills its field from the override, so saving a profile does not pin the score in effect. V6 also clears the stored scores equal to 0.50, the default every profile was given: a score nobody chose is not an override.
- **An edit that drops the alias a profile is spelled by still reaches that profile.** The profiles on an entry were looked up after the entry was replaced, so a profile spelled "redbull" was skipped when the edit removed that alias. They are now collected before the edit and again after it.
- **The name is fixed while editing.** Saving an entry under another name added a second entry and left the first. The field is read-only in edit mode and says how to rename.
- **A removed entry stays removed.** The configured sponsors filled in any name the table lacked, so removing Nike lasted until the next restart. A removed entry keeps its row, marked `removed`; adding the name again brings it back.
- **A name or an alias belongs to one entry.** Two entries could claim one alias and a deal's terms then depended on map order. An upsert that claims another entry's name or alias is a 409 that names the owner; a configured sponsor that would is skipped with a warning rather than stopping the service.
- **Term lists are validated against their columns.** A list longer than 4,000 characters joined, or a term holding a line break (the separator), is a 400 with the reason, not a 500 from the database.

## Deliberately left alone

- **Canonical spelling on the deal.** A deal named "redbull" stays "redbull"; the detection plan's `dealId` on events (logo/02) is the real fix for names drifting, and a suggestion in the form waits until the logo line is quiet in `DealForm`.
- **A term removed from the catalog** stays on the channels that already borrowed it. The alternative, recomputing each profile as catalog terms plus the channel's own, would need the profile to remember which terms were its own; a small later change if the operator asks.
- **Logos in the catalog.** Decided against on 2026-09-22: the detector matches the file the streamer renders, which lives on the deal.

## Verification

Run on 2026-09-29 after merging `main` at `b4054d3` (`logo/02-detection-contract`) and taking the review.

| Check | Command | Result |
|---|---|---|
| sentiment-service and analytics-service | `mvn -pl sentiment-service,analytics-service -Dmaven.gitcommitid.skip=true clean spotless:apply verify` in `maven:3.9-eclipse-temurin-21` | build success; sentiment-service 49 tests, analytics-service 73 tests, 0 failures, 0 errors; Spotless and both coverage floors hold. `clean` matters once for any checkout that built before #81: `target/classes` keeps the old V9 migration, which Maven never deletes on its own, and every analytics-service context then fails as it did on `9d50ee3` |
| Frontend gate | `codegen:check`, `lint`, `format:check`, `test:coverage`, `build` | all pass; 41 files, 171 tests; statements 90.07 %, branches 85.62 % |

New tests: `SponsorCatalogServiceTest` (seeding and the lookup by name or alias; a stored entry is not overwritten; an upsert trims and de-duplicates and a removal leaves a marked row; a removed entry is not brought back and can be added again; a spelling belongs to one entry; terms that do not fit or hold a line break are bad input), `SponsorRelevanceProfileServiceTest` (a sponsor named by alias borrows the entry's terms; a stored profile catches up at start-up; a catalog edit reaches the profiles on it, including one whose alias the edit drops; the minimum score is the channel's own, else the entry's, else the default), `DealsTest` and `ChannelScopeTest` (the sponsors list and its refusal to a streamer), `catalog.test.ts`, and `SponsorCatalog.test.tsx` (the list, the queue, adding from it, editing with the name fixed, removing).

Found on the way, fixed here because it failed this branch's gate: `DealPage.test.tsx`'s deal-editing test used the shared fixture's deal, whose fixed end date was 2026-09-26, so from that day the page rightly hid "End today" and the test failed on `main` for everyone. The test now builds a deal whose dates straddle the clock.

## What to check by hand

1. After the deploy, Operations: the catalog lists Nike, Prime, Razer, and Red Bull from config, and "Named by deals, not in the catalog" is empty, because the owner's "redbull" reaches Red Bull through its alias.
2. `GET /api/sentiment/relevance/sponsors/8wali8` (from the signed-in tab) shows Red Bull's aliases and terms on the owner's "redbull" profile: it caught up at start-up.
3. Edit Red Bull in the catalog: the name field is read-only; add a term and a minimum score, save. The owner's profile gains the term and reads back that score as `minScore` with `minScoreOverride` null.
4. Remove Nike, then `sudo streamsense-deploy` again (or restart sentiment-service): Nike is still gone. Add it again from the form.
5. Add an entry whose alias is "redbull": refused, naming Red Bull.
6. View as a streamer: the Operations page is not there; `GET /api/analytics/deals/sponsors` with a streamer token is 403 `operator_required`.
