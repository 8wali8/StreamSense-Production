import { useEffect, useState } from "react";
import { getSponsorProfile, updateSponsorProfile } from "../../api/sentiment";
import { describeError } from "../../lib/errors";
import { splitTerms } from "../streamer/streamer";
import { useStreamer } from "../streamer/streamer-context";

/**
 * Edit the sponsor profile relevance scoring uses for the selected streamer: the aliases and
 * semantic terms it matches on and the score a line needs to count as sponsor-relevant.
 */
export function SponsorProfileEditor() {
  const { selectedStreamer, sponsorBrand } = useStreamer();
  const [sponsor, setSponsor] = useState(sponsorBrand);
  const [aliases, setAliases] = useState("");
  const [semanticTerms, setSemanticTerms] = useState("");
  const [minScore, setMinScore] = useState("");
  const [status, setStatus] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [stored, setStored] = useState<string | null>(null);

  // What sentiment-service currently matches on for this channel; it survives restarts since 06.
  useEffect(() => {
    let cancelled = false;
    setStored(null);
    getSponsorProfile(selectedStreamer)
      .then((profile) => {
        if (cancelled) return;
        if (!profile) {
          setStored("No stored profile for this channel yet.");
          return;
        }
        setSponsor(profile.sponsor);
        setAliases(profile.aliases.join(", "));
        setSemanticTerms(profile.semanticTerms.join(", "));
        setMinScore(profile.minScore == null ? "" : String(profile.minScore));
        setStored(
          `Stored profile: ${profile.sponsor}, ${profile.aliases.length} aliases, ${profile.semanticTerms.length} terms.`,
        );
      })
      .catch((err: unknown) => {
        if (cancelled) return;
        setStored(
          `Could not read the stored profile: ${describeError(err instanceof Error ? err : new Error("unknown"))}`,
        );
      });
    return () => {
      cancelled = true;
    };
  }, [selectedStreamer]);

  async function save() {
    const name = sponsor.trim();
    if (!name) return;
    const parsedMinScore = minScore.trim() === "" ? undefined : Number(minScore);
    setSaving(true);
    setError(null);
    setStatus(null);
    try {
      await updateSponsorProfile({
        streamer: selectedStreamer,
        sponsor: name,
        aliases: splitTerms(aliases),
        semanticTerms: splitTerms(semanticTerms),
        ...(parsedMinScore !== undefined && Number.isFinite(parsedMinScore) ? { minScore: parsedMinScore } : {}),
      });
      setStatus(`Relevance profile for ${name} saved for @${selectedStreamer}.`);
    } catch (err) {
      setError(describeError(err instanceof Error ? err : new Error("failed to save the sponsor profile")));
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="panel ops-section" aria-label="Sponsor profile">
      <div className="panel-heading">
        <h2>Sponsor profile</h2>
        <p>
          What relevance scoring matches on for @{selectedStreamer}. Merged with the terms configured for the sponsor.
        </p>
      </div>
      {stored && <div className="status-line">{stored}</div>}
      <form
        className="form-grid"
        onSubmit={(event) => {
          event.preventDefault();
          void save();
        }}
      >
        <label className="field">
          <span className="field-label">Sponsor</span>
          <input className="text-input" value={sponsor} onChange={(event) => setSponsor(event.target.value)} />
        </label>
        <label className="field">
          <span className="field-label">Minimum relevance score</span>
          <input
            className="text-input"
            inputMode="decimal"
            value={minScore}
            onChange={(event) => setMinScore(event.target.value)}
            placeholder="configured default"
          />
        </label>
        <label className="field field-wide">
          <span className="field-label">Aliases, comma separated</span>
          <input
            className="text-input"
            value={aliases}
            onChange={(event) => setAliases(event.target.value)}
            placeholder="red bull, redbull, rb"
          />
        </label>
        <label className="field field-wide">
          <span className="field-label">Semantic terms, comma separated</span>
          <input
            className="text-input"
            value={semanticTerms}
            onChange={(event) => setSemanticTerms(event.target.value)}
            placeholder="energy drink, f1, racing"
          />
        </label>
        <div className="form-actions">
          <button className="button-secondary" type="submit" disabled={saving || !sponsor.trim()}>
            {saving ? "Saving..." : "Save profile"}
          </button>
        </div>
      </form>
      {status && <div className="status-line">{status}</div>}
      {error && (
        <div className="error-state" role="alert">
          {error}
        </div>
      )}
    </section>
  );
}
