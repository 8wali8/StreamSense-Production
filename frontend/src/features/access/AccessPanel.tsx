import { clearAuthToken, currentAuthClaims } from "../../lib/auth-token";

type Props = {
  /** A full reload after sign-out drops the Apollo cache and the subscription socket; injectable for tests. */
  reload?: () => void;
};

/**
 * Sidebar footer: who is signed in (a Twitch login and its role) or how long the access link is good
 * for, and the way out. The gate ensures there is a token.
 */
export function AccessPanel({ reload = () => window.location.reload() }: Props) {
  const claims = currentAuthClaims();
  const expiry = claims?.expiry ?? null;
  let line = "Access link";
  if (claims?.login) {
    line = `@${claims.login} · ${claims.role ?? "operator"}`;
  } else if (expiry) {
    line = `Until ${expiry.toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" })}`;
  }
  return (
    <div className="signed-in" aria-label="Access">
      <span className="eyebrow">{claims?.login ? "Signed in as" : "Access"}</span>
      <strong>{line}</strong>
      <button
        type="button"
        className="text-button"
        onClick={() => {
          clearAuthToken();
          reload();
        }}
      >
        Sign out
      </button>
    </div>
  );
}
