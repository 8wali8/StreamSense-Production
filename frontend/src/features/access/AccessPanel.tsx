import { authTokenExpiry, clearAuthToken, readAuthToken } from "../../lib/auth-token";

type Props = {
  /** A full reload after sign-out drops the Apollo cache and the subscription socket; injectable for tests. */
  reload?: () => void;
};

/** Sidebar footer: how long the current access link is good for, and the way out. The gate ensures there is one. */
export function AccessPanel({ reload = () => window.location.reload() }: Props) {
  const token = readAuthToken();
  const expiry = token ? authTokenExpiry(token) : null;
  return (
    <div className="signed-in" aria-label="Access">
      <span className="eyebrow">Access</span>
      <strong>
        {expiry
          ? `Until ${expiry.toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" })}`
          : "Access link"}
      </strong>
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
