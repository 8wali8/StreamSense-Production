# Sponsor Segmentation Implementation Plan

## Objective

Build sponsor detection in stages, starting with frame segmentation and region proposals inside `ml-engine`, then using those regions to improve sponsor/logo detection and sponsor-facing metrics.

The first implementation pass should not break the existing pipeline. The `/ml/sponsor` API should keep returning the current single-detection response while the internals are prepared for real frame analysis.

## Product Direction

The long-term goal is sponsor performance reporting, not generic scene understanding.

Segmentation should be used as a visual foundation to find meaningful regions in a frame. Sponsor detection should remain the primary product layer. General object detection should only be added later when it improves sponsor metrics such as visibility, placement quality, mention alignment, or campaign compliance.

## Existing Pipeline

The current flow is already production-shaped:

1. `video-capture-service` samples Twitch frames with `ffmpeg`.
2. Captured frames are stored in S3/MinIO or filesystem storage.
3. A Kafka `FrameData` event is published to `stream.video.frames`.
4. `video-service` consumes the frame event.
5. `video-service` calls `ml-engine` at `/ml/sponsor`.
6. `ml-engine` reads and validates the frame artifact.
7. `ml-engine` returns a sponsor detection.
8. `video-service` persists the detection and publishes `stream.sponsor.detections`.
9. API gateway, analytics, and frontend consume the sponsor detection stream.

The weak point is the current ML logic: sponsor detection is synthetic and hash-based. The first real improvement is to make `ml-engine` operate on decoded image pixels and produce candidate visual regions.

## Phase 1: Decoded Frame Loading

Goal: make `ml-engine` capable of passing actual frame pixels to vision models.

Files involved:

- `ml-engine/src/main/python/app/frame_store.py`
- `ml-engine/src/main/python/app/main.py`
- `ml-engine/src/main/python/app/sponsor.py`
- `ml-engine/src/test/python/test_sponsor.py`

Implementation steps:

1. Add a `FrameImage` data structure that includes the existing artifact metadata and a decoded PIL image.
2. Add a `load_frame_image(frame_ref)` helper that reads from `file://` or `s3://`, validates the image, converts it to `RGB`, and returns the decoded image.
3. Keep `load_frame_artifact(frame_ref)` available for compatibility by deriving it from the decoded image path.
4. Preserve existing `STREAMSENSE_SPONSOR_REQUIRE_FRAME_READ` behavior.
5. Update `/ml/sponsor` so a successfully loaded frame can be passed to sponsor analysis.
6. Add tests for dimensions, RGB conversion, image signature, missing files, and current endpoint behavior.

Deliverable:

- `ml-engine` can decode frame images once per request and provide pixels to future segmentation/model code.
- The current `/ml/sponsor` response shape remains unchanged.

## Phase 2: Segmentation And Region Proposal Interface

Goal: add a clean interface for finding candidate visual regions without committing to a specific model backend yet.

New file:

- `ml-engine/src/main/python/app/segmentation.py`

Implementation steps:

1. Define a `RegionProposal` data structure with normalized bounding-box fields.
2. Add validation/clamping so region boxes stay inside `[0, 1]` and do not exceed frame boundaries.
3. Add a `SegmentationConfig` data structure with model path, backend, model version, confidence threshold, IoU threshold, and max proposals.
4. Load config from environment variables.
5. Add a `Segmenter` interface with a `propose(image)` method.
6. Add a conservative default segmenter that returns an empty list when no model is configured.
7. Add a deterministic lightweight heuristic proposal path for local testing, based on image dimensions and content variance, without pretending it is sponsor detection.
8. Add tests for proposal validation, configuration, empty-model behavior, and heuristic proposal bounds.

Deliverable:

- `ml-engine` has a stable segmentation/proposal abstraction.
- Future YOLO-seg, ONNX, or SAM-style backends can be plugged into the interface.
- No downstream Java, Kafka, GraphQL, or frontend contracts need to change in this phase.

## Phase 3: Sponsor Detection Uses Region Proposals

Goal: wire the sponsor path through decoded images and region proposals while keeping the API stable.

Implementation steps:

1. Change `compute_sponsor_detection` to accept an optional decoded frame or proposal summary.
2. Generate region proposals when a frame image is available.
3. Use proposals as part of the deterministic transition logic until a real model is configured.
4. Make model version reflect the path used, such as `frame-aware-stub-v1` or `proposal-aware-stub-v1`.
5. Keep the old metadata-only behavior when no frame can be read and frame reads are not required.

Deliverable:

- `/ml/sponsor` is internally ready for real model inference.
- Existing external behavior is preserved for the first pass.

## Phase 4: Real Model Backend

Goal: replace proposal stubs with real segmentation or detection.

Recommended first backend:

- YOLO-style segmentation/detection model, because it is practical to serve inside the current FastAPI `ml-engine`.

Model responsibilities:

- Detect sponsor/logo/product/text-like visual regions.
- Return normalized boxes and confidences.
- Eventually return multiple detections per frame.

Configuration:

- `STREAMSENSE_SPONSOR_MODEL_PATH`
- `STREAMSENSE_SPONSOR_MODEL_BACKEND`
- `STREAMSENSE_SPONSOR_MODEL_VERSION`
- `STREAMSENSE_SPONSOR_DEVICE`
- `STREAMSENSE_SPONSOR_CONFIDENCE_THRESHOLD`
- `STREAMSENSE_SPONSOR_IOU_THRESHOLD`
- `STREAMSENSE_SPONSOR_MAX_PROPOSALS`

Deliverable:

- Real frame pixels produce real candidate regions.
- Sponsor detection can be evaluated visually and quantitatively.

## Phase 5: Multi-Detection Contract

Goal: update the platform to support real scenes with multiple sponsor assets per frame.

Required changes:

- Add a list of detections to the ML response.
- Update Java DTOs in `video-service`.
- Emit one `SponsorDetectionEvent` per detected box.
- Update persistence and schema tests.
- Update API gateway GraphQL models.
- Update frontend overlays to render multiple detections per frame.
- Update analytics consumers.

Recommendation:

- Emit one Kafka event per sponsor box.
- Add `sourceFrameId` and optionally `inferenceRunId` for grouping.
- Do not emit fake sponsor events for no-detection frames.

## Phase 6: Sponsor Exposure Metrics

Goal: turn raw detections into sponsor-facing performance metrics.

Metrics to compute:

- Total visible duration.
- Number of exposure segments.
- Average and longest exposure duration.
- Average confidence.
- Average and peak screen area.
- Placement/prominence score.
- Visual exposure during sponsor mentions.
- Sponsor mentions without visual exposure.
- Visual exposure without verbal mention.
- Reliability and fallback rates.

Implementation direction:

- Use raw detections from `video-service` as evidence.
- Aggregate temporal exposure in `analytics-service`.
- Group detections by sponsor, streamer, stream session, and time.
- Merge short gaps and filter low-confidence detections.

## Phase 7: QA And Reporting

Goal: make model output auditable.

Planned improvements:

- Add debug output for annotated captured frames.
- Show raw detections and boxes on the actual captured frame, not only the live Twitch iframe.
- Add exposure timelines and sponsor score explanations.
- Store model version and data version with detections.
- Add fixture-based model evaluation.

## First Approved Implementation Slice

The first implementation slice covers Phase 1 and Phase 2:

1. Add decoded frame image loading in `ml-engine`.
2. Add a segmentation/proposal module with configuration and safe defaults.
3. Wire `/ml/sponsor` to use the decoded frame and proposal summary internally.
4. Keep the external `/ml/sponsor` response unchanged.
5. Add tests for frame loading and segmentation proposal behavior.
6. Run the `ml-engine` test suite.

This prepares the codebase for real segmentation without forcing a platform-wide schema migration too early.
