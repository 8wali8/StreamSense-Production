# logo/01-deal-logo

The first branch of `docs/planning/sponsor-logo-detection.md` (the plan lands with it): a deal carries the sponsor's logo, so the detector the later branches build has something to look for. Based on `main` at `e120ea3` (#74, deal editing).

## What changed

**analytics-service.**
- `V10__deal_logos.sql`: one row per logo on a deal (object key, content type, sha256, width, height, size, upload time), cascading with the deal.
- `storage/LogoObjectStore`: where the bytes live. `S3LogoObjectStore` is the `streamsense-logos` bucket on the frame store's MinIO, reached with the frame storage credentials through the AWS SDK's S3 client on the JDK's HTTP client (the Netty and Apache clients are excluded; the parent POM imports the SDK BOM), path-style, bounded by `streamsense.logos.connect-timeout-ms` and `read-timeout-ms`, the bucket created on first use. `InMemoryLogoObjectStore` for tests. `config/LogoStorageConfig` picks one by `streamsense.logos.store` (`s3`, the default and what Compose and Kubernetes run, which then requires the endpoint and credentials at start-up; `memory`, set in `src/test/resources/application.yml`).
- `service/DealLogoService`: an upload is a real PNG or JPEG decided from the bytes (`ImageIO` readers, header only, so a file that would unpack to gigabytes is refused by its declared size), within `max-bytes` (5 MB), `min-pixels` (32) and `max-pixels` (8192) per side, and one of at most `max-per-deal` (2). The object goes under `deals/{id}/{uuid}.{png|jpg}`; the row records what was measured; an insert that fails removes the object again. Removing a logo removes its object; deleting a deal (`DealService.delete`) removes its logos' objects after the row cascade.
- `controller/DealLogoController`: `POST /api/analytics/deals/{id}/logos` (multipart `file`, 201 with the logo, 404, 400, 409), `GET .../logos`, `GET .../logos/{logoId}` (the image, privately cacheable for an hour, `nosniff`), `DELETE .../logos/{logoId}` (204, 404). The existing deal pattern in `ChannelScopeFilter` already confines a streamer to their own deals on these paths.
- `api/Deal.logos` (oldest upload first) on every deal read, one query for the whole list; `api/DealLogo` carries `ref`, the `s3://` URI the detection pipeline will read the image by.
- Config: `streamsense.logos.*` and `spring.servlet.multipart.max-file-size` in `config-repo/analytics-service.yml`; the two frame storage secrets mounted into analytics-service in Compose (with `depends_on: minio`) and Kubernetes; the network policy edge analytics-service → minio:9000 and MinIO's matching ingress; `secrets/README.md`.

**api-gateway.** `analytics/DealLogo` and `Deal.logos` (an absent or null list reads as empty, so an older analytics-service or a test fixture without logos still maps), kept in `forSharedView`; `type DealLogo` and `Deal.logos: [DealLogo!]!` in the schema. The storage ref is not exposed.

**Console.**
- `api/analytics.ts`: `uploadDealLogo` (multipart; `api-client.ts` sends a `FormData` body as it is), `removeDealLogo`, `fetchDealLogo` (the bytes as a data URL). The three deal queries select `logos`; `generated.ts` regenerated.
- `features/deals/LogoPicker.tsx`: a drop zone with the file input inside it, PNG or JPEG, up to two, no cropping.
- `DealForm.tsx`: the picker sits with the sponsor and the dates; chosen files are uploaded right after the deal is saved (a logo belongs to a deal that exists), and a failed upload is reported through `onSaved(deal, problem)` without the deal being saved twice. The deals panel and the deal page show the problem as their status line.
- `DealLogos.tsx`: the "Sponsor logo" section on the deal page: the logos (fetched through the API client and shown as data URLs, because an `<img src>` carries no bearer token and the gateway refuses it on the live site) with their pixel size and a Remove each, the picker while there is room, and the sentence that says what they mean ("On-screen tracking is off until you add the sponsor's logo."). A shared tab or the demo sees the sentence only: the image needs a sign-in, and the demo never sends a request.
- `DealPage.tsx`: while the deal has no logo the on-screen tile reads "tracking is off, no logo on the deal" and the media value tile is a dash with "on-screen tracking is off".
- `demo/snapshot.json`: the three recorded deals gained one logo entry each (no bytes; the demo shows a tracked deal, since its numbers come from the stub era and a dash would contradict them). Re-exporting the snapshot from a stack with a real logo is the plan's replay rung.

**Docs.** `docs/contracts/deals.md` (the logo routes and `logos` on the deal), CLAUDE.md (analytics-service's role, the form, the deal page, the API client), the planning index, and the plan itself (the report's "tracking is off" sentence moves to 02, where the summary carries the state).

## Deliberately left alone

- **The session report.** It knows its `dealId` but not the deal's logos; the sentence about tracking being off belongs with `SessionSummary.onScreenTracking` in `logo/02`, not with a second query from the report page.
- **WebP and SVG.** `ImageIO` reads PNG and JPEG out of the box; WebP would need a plugin and SVG a rasteriser and a sanitiser. Both are open questions in the plan; the acceptance names a phone-sized PNG and a JPEG.
- **A concurrent third upload.** The count is checked before the insert, not in a constraint; two uploads racing on a deal with one logo could leave three. A deal has one owner clicking one button; noted, not built.
- **The ops segmentation preview's frame image.** Found on the way: `SegmentationPreview.tsx` puts `frameImageUrl` straight into an `<img src>`, which sends no bearer token, so on the live site (gateway auth on) that picture is a 401. The logo image here fetches through the client instead; the preview should do the same, in its own change.
- **Frame retention and logo history.** A replaced logo's row and object go; which logo produced a detection is recorded on the event in 02 (`logoId`), not kept as a history here.

## Verification

Run on 2026-09-20 in `maven:3.9-eclipse-temurin-21` with `-Dmaven.gitcommitid.skip=true` (the worktree's objects are not in the container) and Node 24 on the Windows checkout.

| Check | Command | Result |
|---|---|---|
| analytics-service | `mvn -pl analytics-service -am spotless:apply verify` | `BUILD SUCCESS`: 63 tests, 0 failures (`DealLogosTest` 3, `DealsTest` 5, `ChannelScopeTest` 2 among them); Spotless, ArchUnit, and the JaCoCo floor hold |
| api-gateway | `mvn -pl api-gateway -am verify` | `BUILD SUCCESS`: 132 tests, 5 skipped (the Redis Testcontainer, no Docker socket in the build container), coverage floor met; `DealsQueryTest` maps `logos[0].width`. A first run hit a 5 s read timeout in `CorrelationIdPropagationIntegrationTest` while another session's Maven container shared the CPU; the rerun was clean |
| Console | `codegen:check`, `lint`, `format:check`, `test:coverage`, `build` | all pass; 39 files, 163 tests (6 new: the logo section and the form upload in `DealPage.test.tsx`, four in `logo-files.test.ts`); coverage floors hold. One coverage run while the Maven container had the CPU saw two timeouts; the rerun and the plain run were clean. |
| Network policies | `tools/k8s/check_network_policies.py` | OK: 61 edges derived, all allowed by 19 policies (analytics-service → minio is one of them) |
| Compose | `docker compose -f docker-compose.yml config -q` | renders |
| Kubernetes | `kubectl kustomize .` | builds (with the example secrets env copied into place) |

New tests: `DealLogosTest` (a PNG and a mislabelled JPEG are kept with their measured shape and served back with the stored type; a third is 409; not-an-image, too small, too large, and empty are 400 with the sentence; an unknown deal is 404; removing a logo frees its object and its slot; deleting the deal leaves no objects; a streamer uploads to and reads from their own deal only, and a logo id reaches its image only through its own deal). `DealsTest` builds `DealService` with the logo service. `DealPage.test.tsx` adds a logo on a deal without one (the tracking-off sentence and the dash give way to the image and the fee multiple), removes it again, and uploads a logo chosen on the new-deal form once the deal exists.

## What to check by hand

1. `make up`, open a deal: the "Sponsor logo" section says tracking is off; drop a PNG on it. The logo shows with its size, the media value tile turns from a dash to a number, and `mc ls` (or the MinIO console on 9001) shows the object under `streamsense-logos/deals/<id>/`.
2. Add a second logo, then try a third: the picker says the deal has its two logos. Remove one: the object is gone from the bucket.
3. Create a deal from the home page with a logo chosen in the form: the deal page opens with the logo already on it.
4. Open the deal's share link in a private window: "Tracking the Red Bull logo on screen." and no image, no picker.
5. Delete a deal (as the operator, after revoking the share link): its objects are gone from the bucket.
