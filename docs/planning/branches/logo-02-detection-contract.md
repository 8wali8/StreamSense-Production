# logo/02-detection-contract

The seam the real detector plugs into, with the placeholder still behind it: what a frame is examined against, what happened to it, and what the report says when nothing could be looked for. Branch 02 of `docs/planning/sponsor-logo-detection.md`, based on `main` after `logo/01` (#79).

## What changed

**Schemas.** `sponsor-detection-event.schema.json` gains optional `outcome` (`DETECTED`, `NOT_DETECTED`, `NO_LOGO`, `UNAVAILABLE`, or null), `dealId`, and `logoId`, all additive. The two `/ml/sponsor` payloads are schema-checked at last: `ml-sponsor-request.schema.json` (the frame plus `sponsor`, `dealId`, `logoId`, `logoRefs`) and `ml-sponsor-response.schema.json` (the detection plus `outcome`, `DETECTED` or `NOT_DETECTED`, absent from an older engine). ml-engine's contract tests and video-service's `EventContractTest` validate samples against them.

**ml-engine.** `SponsorRequest` carries the deal's `sponsor`, `dealId`, `logoId`, and `logoRefs`; `SponsorResponse` an `outcome`. The context the detector sees (`SponsorDetectionContext`) carries the sponsor and the logo refs, and `sponsor.not_detected()` is the answer a real detector gives when it looked and found nothing. The placeholder stays the placeholder: it always says `DETECTED`, but with a sponsor in the request it stamps that name instead of one of its five, so analytics keys the frame under the deal.

**video-service.**
- `client/CurrentDealClient` asks analytics-service for the channel's current deal and logos (`GET /api/analytics/streams/{streamer}/current-deal`) on its own bounded client, keeps each answer, "none" included, for `streamsense.services.analytics-service.current-deal-cache-seconds` (60), and turns a failure into `AnalyticsDependencyException`. The bean exists only with a base URL; without one (local runs without config-repo, the pipeline tests) every frame goes to ml-engine as before.
- `VideoProcessingService`: a channel without a logo is not sent to ml-engine at all and its frame is recorded as `NO_LOGO` (model version `no-logo`, the deal's sponsor when there is a deal, `UNKNOWN` otherwise); a channel whose deal cannot be looked up is `UNAVAILABLE` on the fallback model version, like an engine failure; a channel with a logo is asked about with the sponsor, the ids, and the logo refs, and the engine's outcome is kept (an engine without one reads as `DETECTED`, its fallback as `UNAVAILABLE`). Every frame still yields one event. `streamsense_sponsor_outcomes_total{outcome}` counts them.
- `events/DetectionOutcome` and the event's `outcome`, `dealId`, `logoId`; `V4__detection_outcome.sql` persists them, and a row from before reads as the outcome its model version implies.
- Config: `streamsense.services.analytics-service.*` in `config-repo/video-service.yml`; the network policy edge video-service → analytics-service:8085 and the matching ingress.

**analytics-service.**
- `V11__detection_outcomes.sql`: `examined_detection_count` and `no_logo_detection_count` per bucket and sponsor, beside the existing `fallback_detection_count` (unavailable). `MetricAggregationService` reads the outcome (an event without one is `UNAVAILABLE` when it is the fallback, else `DETECTED`) and tallies: only a `DETECTED` frame can be accepted or low confidence; `NOT_DETECTED` is examined, not low confidence; `NO_LOGO` and `UNAVAILABLE` are neither.
- `SponsorExposureMetric` carries the two new counts. The `sponsorQualityRisk` factor now counts low-confidence and unavailable frames over all frames: a frame that could not be examined is a reason to doubt the numbers, a frame honestly found empty is not.
- `SessionSummary.onScreenTracking` (`state` `ON`, `PARTIAL`, `UNAVAILABLE`, `OFF`, or `NO_FRAMES`; frame counts; `unavailableMs`), from the outcome totals over every sponsor in the session's window. When the logo was not tracked (`OFF`, `UNAVAILABLE`), `logoValue` and `mediaValue` are null; host reads are still priced.
- `GET /api/analytics/streams/{streamer}/current-deal`: the deal covering now, with its logos, 404 when none (`DealService.current`).

**api-gateway.** `outcome`, `dealId`, `logoId` on `SponsorDetectionEvent` (history and subscription); `SessionSummary.onScreenTracking` and `type OnScreenTracking`; the two counts on `SponsorExposureMetric`. `SponsorMomentsComposer` builds on-screen segments from detected frames only, so a frame looked at without the logo never becomes a segment even if it carried a confidence.

**Console.** The session summary query selects `onScreenTracking`. The report's on-screen tile says "tracking is off, no logo on the deal", "tracking was unavailable", "no video captured", or, when some frames could not be examined, how long; the media value tile says why it is a dash (`report-format.ts`: `onScreenLabel`, `mediaValueLabel`). The live strip says the same under the on-screen number. The demo snapshot's summaries gained an `ON` tracking block.

**Docs.** `docs/contracts/sponsor-pipeline.md` (the request, the outcomes, the no-logo and unavailable paths, the current-deal lookup), `sessions.md` (`onScreenTracking` and the value rule), `deals.md` (the current-deal route), the schema index, CLAUDE.md, the branch note.

## Deliberately left alone

- **The real detector.** This branch changes what is asked and answered, not who answers; the placeholder still says `DETECTED` for every frame it is given a logo for. `logo/03` replaces it.
- **The 30 s gap rule and the exposure arithmetic.** An accepted frame still credits one interval; the timeline still joins detections closer than 30 s. `NOT_DETECTED` frames simply do not qualify, which is what the PRD's flicker rule needs from the arithmetic.
- **Recomputing old sessions.** Events from before outcomes read as `DETECTED` (they were the stub's detections), so nothing about existing reports changes.
- **The plan said `sponsorQualityRisk` "from unavailable frames only".** It counts low-confidence detections as well, as it always did, because a found-but-too-weak detection is still a reason to doubt; what it no longer counts is a frame honestly found empty.

## Verification

Run on 2026-09-22 in `maven:3.9-eclipse-temurin-21` with `-Dmaven.gitcommitid.skip=true`, uv on Windows for ml-engine, Node 24 for the console.

| Check | Command | Result |
|---|---|---|
| Schemas | `tools/schema/check_compat.py --base origin/main` | every existing schema OK (the detection event's three new properties are optional); the two sponsor schemas are new |
| ml-engine | `uv run ruff check`, `ruff format --check`, `pytest` | clean; 78 passed, 1 skipped (3 new: the deal's sponsor stamped on the stub's answer, and the request and response against their schemas). mypy could not run here (an application-control policy on this machine blocks the binary); CI runs it |
| video-service | `mvn -pl video-service -am spotless:apply verify` | `BUILD SUCCESS`: 41 tests (6 new in `VideoProcessingServiceTest`, 3 in `CurrentDealClientTest`, 2 in `EventContractTest`), coverage floor met |
| analytics-service | `mvn -pl analytics-service -am spotless:apply verify` | `BUILD SUCCESS`: 73 tests (the outcome tally in `MetricAggregationServiceTest`, the OFF and UNAVAILABLE reports in `SessionSummaryTest`, the current-deal route in `DealLogosTest`), coverage floor met. The first run failed before any test: `main` carries two V9 migrations (see below) |
| api-gateway | `mvn -pl api-gateway -am spotless:apply verify` | `BUILD SUCCESS`: 133 tests, 5 skipped (the Redis Testcontainer), coverage floor met; `SessionReportQueryTest` maps `onScreenTracking`, `SponsorMomentsComposerTest` keeps a not-found frame off the timeline |
| Console | `codegen:check`, `lint`, `format:check`, `test:coverage`, `build` | all pass; 39 files, 167 tests (the tracking-off report in `SessionReportPage.test.tsx`, the two labels in `report-format.test.ts`); floors hold |
| Network policies | `tools/k8s/check_network_policies.py` | OK: 62 edges derived, all allowed (video-service → analytics-service is the new one) |
| Compose, Kubernetes | `docker compose config -q`, `kubectl kustomize .` | both render |

**Found on the way: `main` does not start.** `V9__deal_logos.sql` (#79) and `V9__vod_chat_logs.sql` (the imports work merged between #79's base and its merge) share a version, so Flyway refuses analytics-service on `main` and CI for the #79 merge is red. This branch renames the logo migration to `V10__deal_logos.sql` (its own new one is `V11__detection_outcomes.sql`), and #81 carries the rename alone against `main`. No database had applied the V9 logo migration: the merge was never deployed.

## What to check by hand

1. `make up` with a deal that has a logo: `/ops` shows detections with `outcome=DETECTED` (the stub) stamped with the deal's sponsor, and `streamsense_sponsor_outcomes_total` on video-service's `/actuator/prometheus` grows under `DETECTED`.
2. Remove the logo: within a minute the detections read `outcome=NO_LOGO`, model `no-logo`, and ml-engine's log shows no sponsor requests for the channel. The session report's on-screen tile says tracking is off and the media value is a dash.
3. Stop analytics-service for a minute: detections read `UNAVAILABLE`; the report of that stretch says how long tracking was unavailable once analytics is back.
4. `curl :8085/api/analytics/streams/<login>/current-deal` answers the deal with its logos, and 404 for a channel without one.
