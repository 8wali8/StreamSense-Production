import { useEffect, useState } from "react";
import { fetchDealLogo, removeDealLogo, uploadDealLogo, type Deal, type DealLogo } from "../../api/analytics";
import { describeError } from "../../lib/errors";
import { logosLead, MAX_LOGOS } from "./logo-files";
import { LogoPicker } from "./LogoPicker";

type DealLogosProps = {
  deal: Deal;
  /** A shared tab or the demo: the facts, no image (the bytes need a sign-in) and no controls. */
  sharedView: boolean;
  onChanged: () => void;
};

/** The image, fetched with the bearer token and shown once it is here; a blank stands in until then. Keyed by the logo, so a new logo starts blank. */
function LogoImage({ dealId, logo, alt }: { dealId: string; logo: DealLogo; alt: string }) {
  const [src, setSrc] = useState<string | null>(null);
  useEffect(() => {
    let current = true;
    fetchDealLogo(dealId, logo.id).then(
      (url) => {
        if (current) setSrc(url);
      },
      () => {
        // The facts of the logo are still shown; only the picture is missing.
        if (current) setSrc(null);
      },
    );
    return () => {
      current = false;
    };
  }, [dealId, logo.id]);
  return src ? <img src={src} alt={alt} /> : <span className="logo-pending" aria-hidden="true" />;
}

/** The sponsor's logo on the deal page: the images the detector looks for, with the controls to add and remove them. */
export function DealLogos({ deal, sharedView, onChanged }: DealLogosProps) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const logos = deal.logos;

  async function run(work: () => Promise<void>) {
    setBusy(true);
    setError(null);
    try {
      await work();
      onChanged();
    } catch (err) {
      setError(describeError(err instanceof Error ? err : new Error("the logo change failed")));
    } finally {
      setBusy(false);
    }
  }

  function add(files: File[]) {
    void run(async () => {
      for (const file of files) {
        await uploadDealLogo(deal.id, file);
      }
    });
  }

  function remove(logo: DealLogo) {
    void run(() => removeDealLogo(deal.id, logo.id));
  }

  return (
    <section className="panel deal-logos" aria-label="Sponsor logo">
      <div className="panel-heading">
        <div>
          <h2>Sponsor logo</h2>
          <p>{logosLead(deal.sponsor, logos.length, sharedView)}</p>
        </div>
      </div>
      {!sharedView && logos.length > 0 && (
        <ul className="logo-list">
          {logos.map((logo, index) => (
            <li key={logo.id}>
              <LogoImage key={logo.id} dealId={deal.id} logo={logo} alt={`${deal.sponsor} logo ${index + 1}`} />
              <span className="mono">
                {logo.width}×{logo.height}
              </span>
              <button
                className="button-secondary button-sm"
                type="button"
                disabled={busy}
                onClick={() => remove(logo)}
                aria-label={`Remove ${deal.sponsor} logo ${index + 1}`}
              >
                Remove
              </button>
            </li>
          ))}
        </ul>
      )}
      {!sharedView && <LogoPicker remaining={MAX_LOGOS - logos.length} busy={busy} onFiles={add} />}
      {error && (
        <div className="error-state" role="alert">
          {error}
        </div>
      )}
    </section>
  );
}
