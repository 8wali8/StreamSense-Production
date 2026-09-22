import { useCallback, useEffect, useState } from "react";
import { listSponsorUsage, type SponsorUsage } from "../../api/analytics";
import {
  listSponsorCatalog,
  removeSponsorCatalogEntry,
  saveSponsorCatalogEntry,
  type SponsorCatalogEntry,
} from "../../api/sentiment";
import { describeError } from "../../lib/errors";
import { splitTerms } from "../streamer/streamer";
import { catalogEntryFor, unknownSponsors } from "./catalog";

type Draft = { name: string; aliases: string; semanticTerms: string; minScore: string };

const EMPTY: Draft = { name: "", aliases: "", semanticTerms: "", minScore: "" };

function draftOf(entry: SponsorCatalogEntry): Draft {
  return {
    name: entry.name,
    aliases: entry.aliases.join(", "),
    semanticTerms: entry.semanticTerms.join(", "),
    minScore: entry.minScore == null ? "" : String(entry.minScore),
  };
}

/**
 * The sponsor catalog: what is known about each sponsor across every channel, owned by the operator.
 * A deal that names a sponsor by name or by any alias borrows the entry's terms into the channel's
 * profile, so the entry is tuned once. The sponsors deals name that no entry reaches are listed for
 * the operator to describe; "Add" starts the form with that name.
 */
export function SponsorCatalog() {
  const [catalog, setCatalog] = useState<SponsorCatalogEntry[] | null>(null);
  const [usage, setUsage] = useState<SponsorUsage[]>([]);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [draft, setDraft] = useState<Draft>(EMPTY);
  const [editing, setEditing] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const [entries, used] = await Promise.all([listSponsorCatalog(), listSponsorUsage()]);
      setCatalog(entries);
      setUsage(used);
      setLoadError(null);
    } catch (err) {
      setLoadError(describeError(err instanceof Error ? err : new Error("failed to load the catalog")));
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  async function run(work: () => Promise<string>) {
    setBusy(true);
    setError(null);
    setStatus(null);
    try {
      setStatus(await work());
      await load();
    } catch (err) {
      setError(describeError(err instanceof Error ? err : new Error("the change failed")));
    } finally {
      setBusy(false);
    }
  }

  function save() {
    const name = draft.name.trim();
    if (!name) return;
    const minScore = draft.minScore.trim() === "" ? undefined : Number(draft.minScore);
    void run(async () => {
      const saved = await saveSponsorCatalogEntry({
        name,
        aliases: splitTerms(draft.aliases),
        semanticTerms: splitTerms(draft.semanticTerms),
        ...(minScore !== undefined && Number.isFinite(minScore) ? { minScore } : {}),
      });
      setDraft(EMPTY);
      setEditing(null);
      return `${saved.name} saved: ${saved.aliases.length} aliases, ${saved.semanticTerms.length} terms. Channels on it follow.`;
    });
  }

  function remove(entry: SponsorCatalogEntry) {
    void run(async () => {
      await removeSponsorCatalogEntry(entry.name);
      if (editing === entry.name) {
        setDraft(EMPTY);
        setEditing(null);
      }
      return `${entry.name} removed from the catalog. Channels keep what they borrowed.`;
    });
  }

  const unknown = catalog ? unknownSponsors(usage, catalog) : [];
  const parsedMinScore = draft.minScore.trim() === "" ? null : Number(draft.minScore);
  const mistyped = parsedMinScore != null && !Number.isFinite(parsedMinScore);

  return (
    <section className="panel ops-section" aria-label="Sponsor catalog">
      <div className="panel-heading">
        <div>
          <h2>Sponsor catalog</h2>
          <p>
            What every channel borrows when a deal names a sponsor, by name or by any alias. Tune a sponsor here once.
          </p>
        </div>
      </div>
      {loadError && (
        <div className="error-state" role="alert">
          Failed to load the catalog: {loadError}
        </div>
      )}
      {catalog !== null && catalog.length === 0 && <div className="empty-state">The catalog is empty.</div>}
      {catalog !== null && catalog.length > 0 && (
        <ul className="catalog-list" aria-label="Catalog entries">
          {catalog.map((entry) => {
            const used = usage.filter((row) => catalogEntryFor(row.sponsor, [entry]) !== null);
            const deals = used.reduce((sum, row) => sum + row.deals, 0);
            return (
              <li className="catalog-row" key={entry.name}>
                <span>
                  <strong>{entry.name}</strong>
                  <small>
                    {entry.aliases.length > 0 ? `also ${entry.aliases.join(", ")}` : "no aliases"} ·{" "}
                    {entry.semanticTerms.length} terms
                    {entry.minScore != null ? ` · min score ${entry.minScore}` : ""}
                    {deals > 0 ? ` · ${deals} ${deals === 1 ? "deal" : "deals"}` : " · no deals"}
                  </small>
                </span>
                <span className="catalog-actions">
                  <button
                    className="button-secondary button-sm"
                    type="button"
                    disabled={busy}
                    onClick={() => {
                      setDraft(draftOf(entry));
                      setEditing(entry.name);
                      setStatus(null);
                    }}
                  >
                    Edit
                  </button>
                  <button
                    className="button-secondary button-sm"
                    type="button"
                    disabled={busy}
                    onClick={() => remove(entry)}
                  >
                    Remove
                  </button>
                </span>
              </li>
            );
          })}
        </ul>
      )}
      {unknown.length > 0 && (
        <div className="catalog-unknown" aria-label="Sponsors not in the catalog">
          <div className="field-label">Named by deals, not in the catalog</div>
          <ul className="catalog-list">
            {unknown.map((used) => (
              <li className="catalog-row" key={used.sponsor}>
                <span>
                  <strong>{used.sponsor}</strong>
                  <small>
                    {used.deals} {used.deals === 1 ? "deal" : "deals"} on {used.channels}{" "}
                    {used.channels === 1 ? "channel" : "channels"}
                  </small>
                </span>
                <button
                  className="button-secondary button-sm"
                  type="button"
                  disabled={busy}
                  onClick={() => {
                    setDraft({ ...EMPTY, name: used.sponsor });
                    setEditing(null);
                    setStatus(null);
                  }}
                >
                  Add
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}
      <form
        className="form-grid"
        aria-label={editing ? `Edit ${editing}` : "New catalog entry"}
        onSubmit={(event) => {
          event.preventDefault();
          save();
        }}
      >
        <label className="field">
          <span className="field-label">Sponsor</span>
          <input
            className="text-input"
            value={draft.name}
            onChange={(event) => setDraft({ ...draft, name: event.target.value })}
            placeholder="The sponsor's name"
          />
        </label>
        <label className="field">
          <span className="field-label">Minimum relevance score</span>
          <input
            className="text-input"
            inputMode="decimal"
            value={draft.minScore}
            onChange={(event) => setDraft({ ...draft, minScore: event.target.value })}
            placeholder="configured default"
          />
        </label>
        <label className="field field-wide">
          <span className="field-label">Aliases, comma separated</span>
          <input
            className="text-input"
            value={draft.aliases}
            onChange={(event) => setDraft({ ...draft, aliases: event.target.value })}
            placeholder="other spellings a deal might use"
          />
        </label>
        <label className="field field-wide">
          <span className="field-label">Semantic terms, comma separated</span>
          <input
            className="text-input"
            value={draft.semanticTerms}
            onChange={(event) => setDraft({ ...draft, semanticTerms: event.target.value })}
            placeholder="what chat says when it means this sponsor"
          />
        </label>
        <div className="form-actions">
          <button className="button-secondary" type="submit" disabled={busy || !draft.name.trim() || mistyped}>
            {busy ? "Saving..." : editing ? "Save entry" : "Add entry"}
          </button>
          {(editing || draft.name) && (
            <button
              className="button-secondary"
              type="button"
              disabled={busy}
              onClick={() => {
                setDraft(EMPTY);
                setEditing(null);
              }}
            >
              Clear
            </button>
          )}
        </div>
      </form>
      {mistyped && (
        <div className="status-line" role="alert">
          The minimum score must be a plain number.
        </div>
      )}
      {status && <div className="status-line">{status}</div>}
      {error && (
        <div className="error-state" role="alert">
          {error}
        </div>
      )}
    </section>
  );
}
