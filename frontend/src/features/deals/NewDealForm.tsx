import { useState } from "react";
import { createDeal, type Deal } from "../../api/analytics";
import { describeError } from "../../lib/errors";
import { fromDateInput, toDateInput } from "./deal-format";

type NewDealFormProps = {
  streamer: string;
  defaultSponsor: string;
  onCreated: (deal: Deal) => void;
  onCancel: () => void;
};

function optionalNumber(value: string): number | undefined {
  const trimmed = value.trim();
  if (trimmed === "") return undefined;
  const parsed = Number(trimmed);
  return Number.isFinite(parsed) ? parsed : undefined;
}

/** A non-empty value that is not a number: the form refuses to submit rather than drop the term. */
function isMistypedNumber(value: string): boolean {
  return value.trim() !== "" && optionalNumber(value) === undefined;
}

function optionalText(value: string): string | undefined {
  const trimmed = value.trim();
  return trimmed === "" ? undefined : trimmed;
}

/**
 * The deal's terms. Only the sponsor and start date are required; the rates fall back to the configured ones.
 * A first deal needs the sponsor and the dates, so those sit up front; the fee, the chat signals, and the rates
 * wait behind "More settings", closed until the streamer wants them.
 */
export function NewDealForm({ streamer, defaultSponsor, onCreated, onCancel }: NewDealFormProps) {
  const [sponsor, setSponsor] = useState(defaultSponsor);
  const [starts, setStarts] = useState(toDateInput(Date.now()));
  const [ends, setEnds] = useState("");
  const [promisedStreams, setPromisedStreams] = useState("");
  const [fee, setFee] = useState("");
  const [cpm, setCpm] = useState("");
  const [hostReadRate, setHostReadRate] = useState("");
  const [trackedLink, setTrackedLink] = useState("");
  const [chatCommand, setChatCommand] = useState("");
  const [reward, setReward] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const startsAt = fromDateInput(starts);
  const endsAt = ends.trim() === "" ? null : fromDateInput(ends, true);
  const mistyped = [promisedStreams, fee, cpm, hostReadRate].some(isMistypedNumber);
  const valid = sponsor.trim() !== "" && startsAt != null && (ends.trim() === "" || endsAt != null) && !mistyped;

  async function submit() {
    if (!valid || startsAt == null) return;
    setSaving(true);
    setError(null);
    try {
      const deal = await createDeal({
        streamer,
        sponsor: sponsor.trim(),
        startsAt,
        ...(endsAt != null ? { endsAt } : {}),
        ...(optionalNumber(promisedStreams) !== undefined ? { promisedStreams: optionalNumber(promisedStreams) } : {}),
        ...(optionalNumber(fee) !== undefined ? { fee: optionalNumber(fee) } : {}),
        ...(optionalNumber(cpm) !== undefined ? { cpmPer30sEquivalent: optionalNumber(cpm) } : {}),
        ...(optionalNumber(hostReadRate) !== undefined ? { hostReadRatePer1000: optionalNumber(hostReadRate) } : {}),
        ...(optionalText(trackedLink) !== undefined ? { trackedLink: optionalText(trackedLink) } : {}),
        ...(optionalText(chatCommand) !== undefined ? { chatCommand: optionalText(chatCommand) } : {}),
        ...(optionalText(reward) !== undefined ? { channelPointReward: optionalText(reward) } : {}),
      });
      onCreated(deal);
    } catch (err) {
      setError(describeError(err instanceof Error ? err : new Error("failed to create the deal")));
    } finally {
      setSaving(false);
    }
  }

  return (
    <form
      className="form-grid new-deal"
      aria-label="New deal"
      onSubmit={(event) => {
        event.preventDefault();
        void submit();
      }}
    >
      <label className="field field-wide">
        <span className="field-label">Sponsor</span>
        <input
          className="text-input"
          value={sponsor}
          onChange={(e) => setSponsor(e.target.value)}
          placeholder="The sponsor's name"
        />
      </label>
      <label className="field">
        <span className="field-label">Starts</span>
        <input className="text-input" type="date" value={starts} onChange={(e) => setStarts(e.target.value)} />
      </label>
      <label className="field">
        <span className="field-label">Ends</span>
        <input className="text-input" type="date" value={ends} onChange={(e) => setEnds(e.target.value)} />
        <span className="field-hint">Leave empty for an open-ended deal.</span>
      </label>
      <label className="field">
        <span className="field-label">Promised streams</span>
        <input
          className="text-input"
          inputMode="numeric"
          value={promisedStreams}
          onChange={(e) => setPromisedStreams(e.target.value)}
          placeholder="optional"
        />
      </label>

      <details className="new-deal-more">
        <summary>More settings</summary>
        <p className="field-hint">The fee, the chat signals to count, and the rates the report prices exposure at.</p>
        <div className="form-grid">
          <label className="field">
            <span className="field-label">Fee in USD (private to you)</span>
            <input
              className="text-input"
              inputMode="decimal"
              value={fee}
              onChange={(e) => setFee(e.target.value)}
              placeholder="optional"
            />
          </label>
          <label className="field">
            <span className="field-label">Chat command</span>
            <input
              className="text-input"
              value={chatCommand}
              onChange={(e) => setChatCommand(e.target.value)}
              placeholder="!command"
            />
          </label>
          <label className="field">
            <span className="field-label">CPM per 30-second equivalent, USD</span>
            <input
              className="text-input"
              inputMode="decimal"
              value={cpm}
              onChange={(e) => setCpm(e.target.value)}
              placeholder="configured default"
            />
          </label>
          <label className="field">
            <span className="field-label">Host read rate per 1,000 listeners, USD</span>
            <input
              className="text-input"
              inputMode="decimal"
              value={hostReadRate}
              onChange={(e) => setHostReadRate(e.target.value)}
              placeholder="configured default"
            />
          </label>
          <label className="field">
            <span className="field-label">Tracked link</span>
            <input
              className="text-input"
              value={trackedLink}
              onChange={(e) => setTrackedLink(e.target.value)}
              placeholder="https://"
            />
          </label>
          <label className="field">
            <span className="field-label">Channel point reward</span>
            <input
              className="text-input"
              value={reward}
              onChange={(e) => setReward(e.target.value)}
              placeholder="optional"
            />
          </label>
        </div>
      </details>

      <div className="form-actions">
        <button className="button-primary" type="submit" disabled={saving || !valid}>
          {saving ? "Creating..." : "Create deal"}
        </button>
        <button className="button-secondary" type="button" onClick={onCancel} disabled={saving}>
          Cancel
        </button>
      </div>
      {mistyped && (
        <div className="status-line" role="alert">
          Promised streams, fee, CPM, and host-read rate must be plain numbers (no commas or currency signs).
        </div>
      )}
      {error && (
        <div className="error-state form-error" role="alert">
          {error}
        </div>
      )}
    </form>
  );
}
