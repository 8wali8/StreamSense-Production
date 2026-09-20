# deals/02-edit-deal

A deal could be created, read, shared, and unshared, and nothing else: no edit, no way to end one early, no delete, and `updated_at` was never written. Stacked on `deals/01-first-deal` (#72), whose form this branch reuses; based on `main` at #71.

## What changed

- **`PUT /api/analytics/deals/{id}`** replaces a deal's terms. The body is `DealUpdateRequest`: the create body without `streamer`, which is the deal's and never changes (so a streamer, whom `ChannelScopeFilter` checks against the deal's owner, cannot move a deal to another channel through the body). It is the whole deal, not a patch: a term left out is cleared, a rate left out returns to the configured default. Validation is shared with creation (`DealService.terms`). Sessions are found by the deal's dates at read time, so reports inside the new dates re-price on their next load; nothing is backfilled. Ending a deal is an update whose `endsAt` is now.
- **`DELETE /api/analytics/deals/{id}`** removes a deal for good: 204, 404 when unknown, 409 while shared (revoke the link first, so a sent link never breaks silently). Operators only: the scope filter answers a streamer with 403 `operator_required`, as it does for the Helix status. The share route (`DELETE .../share`) stays the streamer's.
- **Relevance follows.** `DealService.repoint`: after an update or a deletion, if the changed deal is the channel's current one it is pointed at again (its sponsor may have changed); if it was the pointed-at deal and is current no longer, whichever deal is current now is pointed at; when none is, relevance stays where it was, as when a deal ends on its own. Any other deal's change leaves the pointer alone.
- **The console.** `NewDealForm.tsx` is now `DealForm.tsx` and takes an optional `deal`: prefilled, "More settings" open when the deal has any term, "Save changes", and the whole deal sent back with `updateDeal`. The deal page's header gains, for the owner, **Edit** (the form in a panel under the header), **End today** (while the deal has not ended; the whole deal goes back with the end set to now), and, for an operator, **Delete**, which asks in a line first ("Delete this deal for good? Its streams stay") and is refused while shared. A shared tab and the demo see none of these. `api/analytics.ts` gains `updateDeal` and `deleteDeal`.
- **Docs.** `docs/contracts/deals.md` describes both routes and the re-pointing; `CLAUDE.md` names the form's two modes, the deal page's controls, and the scope filter's new refusal.

## Verification

| Check | Command | Result |
|---|---|---|
| analytics-service | `mvn -pl analytics-service -Dmaven.gitcommitid.skip=true spotless:apply verify` in `maven:3.9-eclipse-temurin-21` | build success; 57 tests, 0 failures; JaCoCo total 86 % (floor 83 %) |
| Frontend gate | `npm run lint`, `format:check`, `test:coverage`, `build` | all pass; 156 tests; statements 89.73 %, branches 85.11 % |
| In a browser | `npm run dev` against the local stack from before this branch (its analytics image has no `PUT`), streamer token then operator token | streamer: Edit and End today, no Delete; Edit opens the form prefilled with "More settings" open; operator: Delete asks in a line with Delete and Keep |

New tests: `DealsTest` edits a deal as a whole (sponsor and CPM change, command and link dropped, the rate left out returns to the default, the report inside re-prices), ends it by its end date, refuses deletion while shared, deletes it once unshared; and checks that an edit re-points relevance only when it should. `ChannelScopeTest` checks a streamer edits their own deal and no other's, cannot delete one, and an operator can. `DealPage.test.tsx` walks Edit, End today, and the operator's Delete, and checks a streamer has no Delete.

## What to check by hand

1. After the deploy, open a deal on `https://streamsense.dev/`: Edit, End today, and (as the operator) Delete beside Share. Edit shows the deal's terms; change the sponsor and save; the header and the home page follow.
2. End today on a running deal: the pill turns to Ended, the streams inside stay listed, and the home page no longer follows the sponsor.
3. Share the deal, then Delete: the line says to revoke the link first and the button is disabled. Revoke, Delete, and the home page opens without the deal.
4. View as a streamer: Edit and End today are there, Delete is not.
