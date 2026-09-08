# Product direction: from live console to sponsorship proof

Agreed 2026-09-08 (draft 2026-09-07). Shareable version: https://claude.ai/code/artifact/b8db5efc-29f9-49af-853a-ea57587607e7. Branch plan: `README.md` in this folder.

## Thesis

StreamSense is the proof of performance for one sponsorship deal on Twitch: logo time on screen, what the streamer said about the product, how chat reacted, and whether anything risky happened near the brand. Marketplaces (Twitch + StreamElements, Streamlabs + Lurkit, Powerspike) own matching; enterprise vendors (Shikenso, Stream Hatchet, Streams Charts, Relo, Zoomph) sell measurement to rights holders and events. Nobody sells this proof self-serve to the streamer or manager who negotiates the deal.

Built streamer-first: the streamer owns the channel (authorizes capture) and the report they send a sponsor is how the sponsor discovers the product. The brand view is the same report read from the other side.

## Personas

| Persona | Role in the product |
|---|---|
| Streamer (300 to 5k CCV, negotiates own deals) | Primary user |
| Manager / small agency (roster of 5 to 30) | First paying customer |
| Brand manager at an endemic sponsor | Receives shared reports, second-phase customer |
| Operator (us) | Runs the pipeline; separate ops page |

## User stories (capped at seven)

| # | Question | Answer | Page |
|---|---|---|---|
| S1 | Is the sponsor getting what I promised, right now? | Exposure so far, mentions so far with sentiment, current risk | Streamer home, live strip |
| S2 | What did the sponsor get from this stream? | Exposure minutes and share, mentions (voice + chat), mention sentiment, viewers; then a value section: media value with the formula shown (prominence-weighted logo viewer-minutes at a per-deal CPM, plus host reads at a per-listener rate), value against fee (private to the streamer), and direct response (tracked link clicks, chat command uses, channel point redemptions). Sentiment is never folded into the dollar figure. | Session report header and value section |
| S3 | Show me the moments | One timeline: logo segments, voice mentions, chat bursts, negative spikes, VOD jump | Session report timeline |
| S4 | Did anything risky happen near the brand? | Risk level with named factors, spikes on the same timeline | Session report, live strip |
| S5 | How is this deal going overall? | Deal totals, per-stream trend, session list | Deal page |
| S6 | What do I send the sponsor? | Permanent read-only link, phone-friendly | Session and deal pages, shared view |
| S7 | What is my track record, to set a rate? | Exposure per sponsored hour, mention sentiment, audience per stream, across deals | Streamer home, history |

Brand-side stories are the mirror of S2 to S7 and exist to check the shared view against a second reader, not to build a second product.

## Cuts

Removed: campaign goal field; per-panel streamer inputs and load buttons; event IDs, model versions, frame refs, box coordinates, sequence numbers; the recommendations panel (no story asks for it; the service is removed from the stack in a later branch); hard-coded roster with fake owner and risk.

Moved to `/ops`: streamer and sponsor form and runtime switching status; health, ingestion, capture pills; raw event lists.

## Routes

- `/` streamer home: live strip (player, detections, brand-mention feed; chat and transcript in a drawer), active deals, history. S1, S7.
- `/deals/:dealId` deal page: header totals, per-stream trend, sessions list, share control. S5, S6.
- `/sessions/:sessionId` session report: header numbers with risk, timeline, best and worst moment, brand mentions, stream context. S2, S3, S4, S6. This is the product; build first among customer pages.
- `/ops` operations: pipeline status and replay controls, channel control form, sponsor profile editor, raw event tables. Build first overall because it is relocation.

## Model

- Sponsor (durable): sponsorId, name, aliases[], semanticTerms[], detectionLabel. The in-memory relevance profile made durable.
- Deal (new): dealId, streamer, sponsorId, startsAt, endsAt, promisedStreams?, shareToken, fee?, cpm, hostReadRate, trackedLink?, chatCommand?, channelPointReward?.
- Session (needs a boundary): streamSessionId, streamer, twitchStreamId, startedAt, endedAt, title, category, peakViewers, averageViewers, dealId (derived). Today the id is minted per capture start, so restarting capture splits one stream into two.
- Session metrics (exists per bucket): exposureMs, exposureShare, mentionCount, mentionSentiment, risk; new: mediaValue, moments[].

## Backend gaps, ranked

1. **Now** Poll Twitch Helix streams per watched channel: live state, stream id, title, category, viewers. Opens and closes sessions; feeds media value.
2. **Now** Sessions table and query in analytics-service (list by streamer and range, get by id with totals). Capture and chat carry the Helix stream id as the session id.
3. **Now** Analytics queries by session id alone or absolute from/to on summary, timeseries, exposure, brand safety.
4. **Next** Direct response counters: chat-service counts uses of the deal's chat command and posts of its tracked link per session (chat is already ingested); a per-deal short link records clicks; channel point redemptions come from Twitch EventSub later. Media value needs only viewer counts plus the deal's CPM and rates.
5. **Next** Sponsor moments query: detection runs collapsed into segments plus relevant voice and chat lines with VOD timestamps. Client-side first is acceptable.
6. **Next** Durable sponsors and deals with REST endpoints, seeded from config-repo. Creating a deal points relevance at the channel.
7. **Next** Read-only share token on deals.
8. **Later** Twitch OAuth for streamers.

The demo needs only the first three (gap 4 is small and demo-worthy if time allows): the replay alias then yields a real session report from stored data.

## References per page

- Session report: Twitch Stream Summary (after-stream mental model).
- Shell: Twitch Creator Dashboard (navigation, familiar terms).
- Metric vocabulary: Stream Hatchet Brands, Streams Charts (exposure time, logo share, average logo size, media value).
- Deal page and history: Streams Charts channel pages (permanent URLs, history table under a trend).
- Risk: CreatorScore (one score, named weighted drivers).

## Next steps

1. Stories and cuts agreed 2026-09-08. Recommendations dropped entirely.
2. Mock the four pages as a design canvas; session report in most detail.
3. Start gaps 1 to 3 in parallel.
4. Rebuild route by route: ops, session report against the replay alias, streamer home, deal page.
