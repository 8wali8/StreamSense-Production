# Sponsor Detection Improvement Plan

## Recommendation

For the current product goal, prioritize sponsor/logo detection rather than broad object detection across everything in the scene.

The end goal is to give sponsors useful metrics about how well streamers are delivering sponsor value. That means the system should optimize for brand exposure, placement quality, duration, confidence, and contextual relevance. A generic object detector can identify people, chairs, monitors, bottles, keyboards, game UI, and other scene objects, but most of that data will not directly answer sponsor-facing questions. It also increases compute cost, model noise, storage volume, schema complexity, and the amount of downstream filtering required before the data becomes useful.

The best path is sponsor-first with an extensible detection foundation:

- Build a real sponsor/logo detection pipeline now.
- Preserve the ability to add general object or scene-context models later.
- Use optional scene signals only when they improve sponsor metrics, such as whether a logo is on-screen during gameplay, near the streamer face/camera box, or visible during a product mention.

This gives the fastest route to sponsor-grade metrics while avoiding a broad object-detection project that may produce lots of irrelevant detections.

## Current State

The existing pipeline is already shaped like a production detection system, but the ML step is currently a deterministic stub.

The flow is:

1. `video-capture-service` samples Twitch frames with `ffmpeg`.
2. Captured frames are stored in S3/MinIO or filesystem storage.
3. A Kafka `FrameData` event is published to `stream.video.frames`.
4. `video-service` consumes the frame event.
5. `video-service` calls `ml-engine` at `/ml/sponsor`.
6. `ml-engine` attempts to read and validate the image artifact.
7. `ml-engine` returns one synthetic sponsor detection.
8. `video-service` persists the detection and publishes it to `stream.sponsor.detections`.
9. The API gateway, analytics service, and frontend consume/display the detection.

Important code locations:

- Frame capture: `video-capture-service/src/main/python/app/frame_sampler.py`
- Frame capture loop and event publishing: `video-capture-service/src/main/python/app/capture_loop.py`
- Frame event shape: `video-capture-service/src/main/python/app/kafka_publisher.py`
- Kafka frame consumer: `video-service/src/main/java/com/streamsense/videoservice/kafka/VideoFrameConsumer.java`
- Video processing handoff to ML: `video-service/src/main/java/com/streamsense/videoservice/service/VideoProcessingService.java`
- ML client resilience/fallback: `video-service/src/main/java/com/streamsense/videoservice/client/MlEngineClient.java`
- ML sponsor endpoint: `ml-engine/src/main/python/app/main.py`
- Frame artifact loading: `ml-engine/src/main/python/app/frame_store.py`
- Current synthetic sponsor logic: `ml-engine/src/main/python/app/sponsor.py`
- Sponsor detection event schema: `docs/schemas/sponsor-detection-event.schema.json`
- Frontend display: `frontend/src/components/SponsorPanel.tsx` and `frontend/src/App.tsx`

Current limitation:

- The system does not actually analyze pixels for logos, products, or objects.
- `ml-engine/src/main/python/app/sponsor.py` hashes frame metadata and optional frame signature to produce a fake sponsor, confidence, and bounding box.
- The frontend overlay uses the returned box values, but those values are synthetic.

## Product Goal

The sponsor-facing goal should be to answer questions like:

- How often did the sponsor appear on stream?
- How long was the sponsor visible?
- How prominent was the sponsor on-screen?
- Was the sponsor visible during high-value moments?
- Did the streamer verbally mention the sponsor while the logo/product was visible?
- Was sponsor exposure consistent across the stream?
- Was the sponsor shown in a way that is likely valuable, such as large enough, centered enough, unobstructed enough, and visible for enough time?

Those questions require sponsor-specific detection and temporal aggregation more than broad scene understanding.

## Sponsor-Only vs Full Object Detection

### Sponsor-Only Detection First

Sponsor-only detection is the better first implementation for this product.

Benefits:

- Directly aligned with sponsor metrics.
- Lower compute cost than detecting every object class in every sampled frame.
- Lower false-positive surface area.
- Simpler schemas, dashboards, and analytics.
- Easier model evaluation because labels are limited to known brands/logos/products.
- Easier to explain to sponsors because every detection is sponsor-relevant.
- Easier to tune thresholds per sponsor or campaign.

Tradeoffs:

- It may miss useful context, such as where the streamer camera is or what else is happening in the scene.
- It may require brand-specific training data or logo templates.
- New sponsors may need onboarding before detection quality is strong.

### Full Object Detection First

Full object detection should not be the first priority unless the product needs general scene intelligence.

Benefits:

- Can provide extra context such as person, face/camera area, product category, bottle, keyboard, monitor, chair, game screen, or overlay elements.
- Can help distinguish actual product placement from background clutter.
- Can support future features unrelated to sponsors.

Costs:

- More compute-heavy.
- More noisy for sponsor analytics.
- More downstream filtering and storage.
- Generic detectors usually do not know sponsor logos unless trained for them.
- Generic detections do not automatically translate into sponsor value.

Recommended compromise:

- Build sponsor/logo detection as the primary model.
- Add a small number of contextual detectors only when they improve sponsor metrics.
- Examples: face/camera-box detection, scene-change detection, OCR for sponsor text, product/package detection for campaign-specific assets.

## Target Detection Model

The first real model should detect sponsor-relevant visual assets:

- Brand logos.
- Sponsor text marks.
- Product packaging.
- Campaign-specific overlays.
- Branded panels or stream overlays.

Good model options:

- Fine-tuned YOLO model for logo/product detection.
- ONNX Runtime model for faster CPU deployment.
- PyTorch/Ultralytics model for fast iteration.
- A hybrid approach combining logo detection, OCR, and image similarity.

Suggested first pass:

- Use YOLO-based detection for bounding boxes.
- Add OCR later for sponsor text or promo codes.
- Add perceptual matching/template matching later for sponsors with very stable logo assets.

## Response Contract Improvements

The current `/ml/sponsor` endpoint returns a single detection:

- `sponsor`
- `confidence`
- `modelVersion`
- `x`
- `y`
- `width`
- `height`

Real sponsor detection should return multiple detections per frame. A frame can contain several sponsor assets, or the same sponsor can appear in multiple locations.

Proposed ML response shape:

```json
{
  "modelVersion": "sponsor-yolo-v1",
  "detections": [
    {
      "sponsor": "Nike",
      "assetType": "logo",
      "confidence": 0.92,
      "x": 0.12,
      "y": 0.18,
      "width": 0.31,
      "height": 0.24
    }
  ]
}
```

Potential additional fields:

- `classId`
- `assetId`
- `assetType`, such as `logo`, `product`, `text`, `overlay`, `unknown`
- `visibilityScore`
- `areaRatio`
- `centerDistance`
- `occlusionEstimate`
- `thresholdUsed`
- `inferenceLatencyMs`

This requires downstream changes in:

- `ml-engine/src/main/python/app/models.py`
- `video-service/src/main/java/com/streamsense/videoservice/dto/MlSponsorResponse.java`
- `video-service/src/main/java/com/streamsense/videoservice/events/SponsorDetectionEvent.java`
- `docs/schemas/sponsor-detection-event.schema.json`
- persistence tables/entities in `video-service`
- GraphQL event/query models in `api-gateway`
- frontend queries, subscriptions, and overlays
- analytics aggregation logic

## Detection Event Model

The current `SponsorDetectionEvent` can represent one detection for one frame. For real detection, choose one of two approaches.

Option A: emit one event per detected sponsor box.

- Minimal disruption to Kafka consumers.
- Current event shape mostly survives.
- A single frame with three logos emits three events.
- Easier to aggregate exposure per sponsor.

Option B: emit one event per frame with nested detections.

- More faithful to model output.
- Better for frame-level debugging.
- Requires larger schema changes.
- More complicated for consumers that want per-sponsor aggregates.

Recommendation:

- Use one event per detected sponsor box for the near term.
- Add a shared `inferenceRunId` or `sourceFrameId` so events from the same frame can be grouped.
- Consider a separate frame-level inference event later if debugging needs grow.

## Temporal Tracking

Single-frame detections are not enough for sponsor metrics. Sponsor value depends on exposure over time.

Add temporal tracking that groups detections across consecutive sampled frames.

Useful tracking logic:

- Match detections by sponsor and bounding-box IoU.
- Smooth confidence over a rolling window.
- Require minimum consecutive detections before counting exposure.
- Allow short gaps so one missed frame does not end an exposure segment.
- Track start time, end time, duration, average confidence, max confidence, and average screen area.

Example exposure segment:

```json
{
  "sponsor": "Nike",
  "streamer": "example_streamer",
  "streamSessionId": "example_streamer-1710000000000",
  "startedAt": 1710000000000,
  "endedAt": 1710000025000,
  "durationMs": 25000,
  "averageConfidence": 0.88,
  "averageAreaRatio": 0.07,
  "peakAreaRatio": 0.12,
  "frameCount": 5
}
```

This should become the primary source for sponsor exposure analytics, while raw detection events remain useful for debugging and auditing.

## Sponsor Metrics To Produce

Raw detections should roll up into sponsor-facing metrics.

Recommended metrics:

- Total visible duration per sponsor.
- Number of distinct exposure segments.
- Average exposure segment duration.
- Longest continuous exposure.
- Average confidence.
- Average screen area percentage.
- Peak screen area percentage.
- Time visible in the center third of the screen.
- Time visible while streamer audio mentions sponsor.
- Time visible during high chat engagement.
- Time visible during positive/negative sentiment windows.
- Missed-read windows where a sponsor was visible but not mentioned.
- Mention-without-visual windows where the streamer mentioned a sponsor but the logo/product was not visible.
- Exposure consistency across the stream.

Campaign-specific metrics:

- Required sponsor asset appeared at least once.
- Required sponsor asset appeared for minimum duration.
- Sponsor logo was visible during CTA/promo-code mention.
- Sponsor appeared within required time window.
- Sponsor was not blocked, tiny, or low-confidence for most of the exposure.

## Frame Capture Improvements

The current default frame sampling interval is 10 seconds. That is acceptable for a prototype, but it can miss short sponsor appearances.

Recommended improvements:

- Lower sampling interval for sponsor monitoring, likely 1-3 seconds for active campaigns.
- Allow per-channel or per-campaign sampling configuration.
- Use burst sampling around likely sponsor moments, such as transcript mentions or chat spikes.
- Add scene-change detection to avoid wasting inference on nearly identical frames.
- Preserve frame dimensions and maybe perceptual hashes in the event metadata.
- Consider storing thumbnails or annotated debug frames for review.

Current code enforces `TWITCH_VIDEO_SAMPLE_INTERVAL_SECONDS >= 5`, so that validation should be revisited if high-frequency sampling is needed.

## Image Preprocessing

Before inference, standardize frame handling in `ml-engine`.

Recommended preprocessing:

- Decode image once and pass pixel data to the model.
- Normalize orientation and color format.
- Resize to model input dimensions while preserving aspect-ratio mapping.
- Keep original width and height so normalized boxes can be mapped accurately.
- Reject invalid or tiny images.
- Record image dimensions and checksum for traceability.

The existing `frame_store.py` already validates image artifacts and computes a signature. That can be extended to return decoded image data or a PIL image object for inference.

## Model Serving

Short-term serving can live inside `ml-engine`.

Recommended short-term approach:

- Load model once at process startup or on first request.
- Keep model object cached globally.
- Add environment variables for model path, confidence threshold, IoU threshold, and device.
- Support CPU by default, GPU when available.
- Expose model version in every response.

Recommended environment variables:

- `STREAMSENSE_SPONSOR_MODEL_PATH`
- `STREAMSENSE_SPONSOR_MODEL_VERSION`
- `STREAMSENSE_SPONSOR_CONFIDENCE_THRESHOLD`
- `STREAMSENSE_SPONSOR_IOU_THRESHOLD`
- `STREAMSENSE_SPONSOR_DEVICE`
- `STREAMSENSE_SPONSOR_REQUIRE_FRAME_READ`

Medium-term serving options:

- Keep inference in `ml-engine` if throughput is modest.
- Split model serving into a separate GPU-backed service if inference becomes a bottleneck.
- Export to ONNX for faster and more portable runtime.

## Fallback Behavior

Current fallback behavior emits an `UNKNOWN` sponsor detection when ML dependency calls fail.

That is useful for keeping the pipeline alive, but sponsor analytics should treat fallback detections carefully.

Recommended behavior:

- Persist fallback events for observability.
- Exclude fallback events from sponsor exposure metrics.
- Surface fallback rate as a reliability metric.
- Alert if fallback rate crosses a threshold.
- Consider failing the frame silently instead of emitting an exposure-like event if sponsors will consume the metric directly.

For real detection, frame reads should usually be required. If the frame cannot be read, the model cannot produce a trustworthy detection.

## Frontend Improvements

Current frontend overlays detection boxes on the live Twitch iframe. That can be visually misleading because the detection was generated from a captured frame, while the live player may be several seconds ahead or behind.

Recommended improvements:

- Display the actual captured frame artifact with boxes in a review/debug panel.
- Keep the live Twitch iframe for context, but avoid implying exact overlay alignment unless latency is handled.
- Add a timeline of sponsor exposure segments.
- Show sponsor visibility score, duration, and confidence trend.
- Add a QA view showing raw detections, annotated frames, and model version.

Useful sponsor-facing dashboard elements:

- Exposure timeline.
- Top sponsors by visible duration.
- Sponsor mention and visual alignment.
- Campaign requirement checklist.
- Streamer performance score with explainable components.

## Analytics Improvements

The analytics layer should move from counting raw detections to computing sponsor exposure quality.

Recommended aggregation levels:

- Raw detection event.
- Frame-level inference result.
- Exposure segment.
- Stream/session-level sponsor summary.
- Campaign-level sponsor summary.

Suggested quality score components:

- Duration score.
- Confidence score.
- Prominence score based on screen area.
- Placement score based on center/edge weighting.
- Mention-alignment score from transcript matching.
- Engagement score from chat volume and sentiment during exposure.
- Reliability penalty from fallback/error rate.

Example sponsor performance score:

```text
score = duration_weight * duration_score
      + prominence_weight * prominence_score
      + mention_weight * mention_alignment_score
      + engagement_weight * engagement_score
      - reliability_penalty
```

This should be explainable in the UI so sponsors can understand why a streamer scored well or poorly.

## Data And Training

Sponsor/logo detection quality will depend heavily on training data.

Recommended data strategy:

- Collect representative stream frames from real target channels.
- Label sponsor logos/products with bounding boxes.
- Include negative examples with no sponsor.
- Include hard negatives such as similar logos, UI graphics, game brands, and cluttered overlays.
- Include multiple resolutions, themes, layouts, lighting conditions, and motion blur.
- Track dataset version and model version together.

For new sponsors:

- Collect official logo files and product images.
- Generate synthetic placements on representative stream screenshots for bootstrapping.
- Add real stream samples as soon as available.
- Fine-tune or update class mappings per campaign.

Evaluation metrics:

- Precision by sponsor.
- Recall by sponsor.
- mAP at IoU thresholds.
- False-positive rate per hour of stream.
- Missed exposure duration.
- Segment-level duration error.
- Mention-alignment accuracy.

## Testing Plan

ML tests:

- Valid image returns valid detections.
- Missing image fails when frame read is required.
- Invalid image fails cleanly.
- Confidence and box ranges are valid.
- Multiple detections are returned correctly.
- Thresholds filter low-confidence detections.
- Model version is included.

Java service tests:

- Multiple ML detections create multiple persisted events.
- Empty detection response creates no sponsor exposure event or creates a frame-level no-detection record, depending on chosen contract.
- ML failure fallback is excluded from sponsor metrics.
- Event schemas remain valid.

Frontend tests:

- Multiple detections render correctly.
- Fallback events are labeled clearly.
- Exposure segments display duration and score.
- Captured-frame overlay uses normalized boxes correctly.

Analytics tests:

- Consecutive detections become one exposure segment.
- Short gaps are merged correctly.
- Low-confidence detections are excluded or down-weighted.
- Sponsor mention alignment is computed correctly.

## Phased Implementation Roadmap

### Phase 1: Real Single-Detection Model Behind Existing Contract

Goal: replace synthetic logic without changing the entire platform contract.

Tasks:

- Add a real image inference module in `ml-engine`.
- Load a sponsor/logo model from an environment-configured path.
- Return the highest-confidence sponsor detection using the current response shape.
- Keep fallback behavior unchanged.
- Add fixture-based ML tests.
- Add model latency and detection count logging.

This phase gives immediate quality improvement with minimal downstream changes.

### Phase 2: Multiple Detections Per Frame

Goal: represent real model output accurately.

Tasks:

- Change `/ml/sponsor` response to return `detections`.
- Update Java DTOs.
- Emit one `SponsorDetectionEvent` per detected box.
- Update schema docs and contract tests.
- Update persistence and recent-detection APIs.
- Update GraphQL and frontend overlays.

This phase makes the data model compatible with real scenes.

### Phase 3: Temporal Exposure Segments

Goal: convert raw detections into sponsor-facing exposure metrics.

Tasks:

- Add detection grouping by sponsor, stream session, and time.
- Track continuous exposure windows.
- Add duration, average confidence, area, and prominence metrics.
- Exclude fallback detections from exposure metrics.
- Add exposure segment APIs and dashboard views.

This phase turns detections into business value.

### Phase 4: Mention And Context Alignment

Goal: measure whether streamers are delivering sponsor messaging well.

Tasks:

- Align transcript segments with sponsor exposure windows.
- Detect sponsor mentions, promo-code mentions, and CTA language.
- Compare visual exposure with verbal mentions.
- Add engagement and sentiment during exposure windows.
- Add sponsor performance score explanations.

This phase directly supports sponsor reporting.

### Phase 5: Optional Scene Context Models

Goal: add only the generic detection/context that improves sponsor metrics.

Candidate additions:

- Face/camera region detection.
- OCR for text logos and promo codes.
- Stream overlay detection.
- Product/package detector for campaign-specific assets.
- Scene-change detector to optimize sampling.

Avoid broad object detection unless a specific sponsor metric requires it.

## Risks

Main risks:

- Logo detection can produce false positives on similar marks.
- Small logos may be hard to detect in compressed Twitch frames.
- The live Twitch iframe may not align with captured-frame detections.
- Sponsors may require high precision before trusting metrics.
- New sponsors may need new labeled data.
- Higher sampling frequency increases storage, Kafka traffic, and inference cost.
- CPU-only inference may become too slow at scale.

Mitigations:

- Use confidence thresholds per sponsor.
- Require minimum exposure duration before counting sponsor value.
- Keep annotated-frame QA tools.
- Track model/data versions in all events.
- Start with a limited sponsor set and expand based on labeled data quality.
- Add scene-change or adaptive sampling to control inference volume.

## Success Criteria

The improved sponsor detection system should be considered successful when:

- Sponsor detections are based on actual frame pixels.
- Multiple sponsor assets per frame can be represented.
- Raw detections are aggregated into exposure durations.
- Fallback and frame-read failures are visible but excluded from sponsor value metrics.
- Sponsors can see explainable metrics for duration, prominence, confidence, and mention alignment.
- The system can onboard new sponsors with predictable data/model workflow.
- The UI can show both high-level sponsor metrics and auditable detection evidence.

## Bottom Line

Do sponsor detection first, not full object detection first.

The product is not trying to understand every object in a stream. It is trying to measure sponsor delivery. A sponsor-specific detector, supported by temporal tracking and transcript alignment, will produce more accurate and more useful sponsor metrics than a generic object detector. Full scene/object understanding can be added later as a supporting signal, but it should not drive the initial implementation.
