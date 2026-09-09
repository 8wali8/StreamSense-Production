import { useState } from "react";
import { createShareLink, revokeShareLink } from "../../api/analytics";
import { describeError } from "../../lib/errors";
import { shareUrl } from "../../lib/share-token";

type ShareControlProps = {
  dealId: string;
  shareToken: string | null;
  /** Called after the token changed so the page refetches the deal. */
  onChanged: () => void;
};

/** S6: the read-only link a streamer sends the sponsor. Anyone with it sees the deal and its reports, never the fee. */
export function ShareControl({ dealId, shareToken, onChanged }: ShareControlProps) {
  const [busy, setBusy] = useState(false);
  const [copied, setCopied] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function run(action: () => Promise<unknown>) {
    setBusy(true);
    setError(null);
    try {
      await action();
      onChanged();
    } catch (err) {
      setError(describeError(err instanceof Error ? err : new Error("share failed")));
    } finally {
      setBusy(false);
    }
  }

  if (!shareToken) {
    return (
      <div className="share-control">
        <button
          className="button-secondary button-sm"
          type="button"
          disabled={busy}
          onClick={() => void run(() => createShareLink(dealId))}
        >
          {busy ? "Sharing..." : "Share"}
        </button>
        {error && (
          <span className="error-text" role="alert">
            {error}
          </span>
        )}
      </div>
    );
  }

  const url = shareUrl(dealId, shareToken);
  return (
    <div className="share-control">
      <input
        className="text-input share-url"
        aria-label="Share link"
        readOnly
        value={url}
        onFocus={(e) => e.target.select()}
      />
      <button
        className="button-secondary button-sm"
        type="button"
        onClick={() => {
          void navigator.clipboard?.writeText(url).then(
            () => setCopied(true),
            () => setCopied(false),
          );
        }}
      >
        {copied ? "Copied" : "Copy"}
      </button>
      <button
        className="button-secondary button-sm"
        type="button"
        disabled={busy}
        onClick={() => void run(() => revokeShareLink(dealId))}
      >
        Revoke
      </button>
      {error && (
        <span className="error-text" role="alert">
          {error}
        </span>
      )}
    </div>
  );
}
