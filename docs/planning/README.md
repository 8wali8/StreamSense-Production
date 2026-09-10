# Planning

- `production-hardening.md` (on `hardening/00-plan`): the hardening plan, verification ladder, and branch sequence.
- `production-hardening-followups.md`: the second series (branches 21 onward), collected from the follow-ups the first fourteen branches recorded.
- `cloud-hosting.md`: the plan for running the Compose stack on one GCP VM (branches `cloud/00` to `cloud/04`): decisions, verification ladder, branch sequence, phase two.
- `handoffs/`: self-contained briefs for a fresh session to continue a piece of work (`console-auth.md`: signing in without pasting a token; `helix-poller.md`: turning on the Twitch Helix poller on the VM).
- `branches/`: one note per hardening branch with what changed, what was left alone, how it was verified, and what the reviewer should check by hand.
- `history/`: earlier planning and hand-off documents kept for context, not maintained:
  - `roadmap-12-week.md`, `production-gap-plan.md`: the original roadmap and gap plan.
  - `current-state.md`, `next-work.md`: the replay-milestone snapshot and backlog as of that milestone.
  - `performance-report.md`, `production-changes.md`, `documentation-links.md`: reports from earlier sprints.
  - `plans/`: feature plans (real sentiment, sponsor detection, relevance, segmentation, VOD replay).
  - `production-port-plans/`, `sprint-plans/`: the phase and weekly plans the earlier sprints followed.
  - `session-logs/`: transcripts of earlier agent sessions.

Current runbooks stay in `docs/`: `howtorun.md`, `kubernetes-kind.md`, `replay-runbook.md`, `degraded-path-proof.md`, `architecture.md`.
