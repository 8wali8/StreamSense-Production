import { useState } from "react";
import { clearAuthToken, currentAuthClaims, isOperatorSession } from "../../lib/auth-token";
import { readViewAs, startViewAs, stopViewAs } from "../../lib/view-as";

type Props = {
  /** A full reload after sign-out drops the Apollo cache and the subscription socket; injectable for tests. */
  reload?: () => void;
  /** Starting or ending a "view as" goes back to the home and reloads, so every page re-reads the channel. */
  openHome?: () => void;
};

/**
 * Sidebar footer: who is signed in (a Twitch login and its role) and the way out. An operator can also
 * view the console as a streamer: pinned to that channel, without the operations page, until they
 * stop. The gate ensures there is a token.
 */
export function AccessPanel({
  reload = () => window.location.reload(),
  openHome = () => window.location.assign("/"),
}: Props) {
  const claims = currentAuthClaims();
  const viewingAs = readViewAs();
  const operator = isOperatorSession();
  const [viewAsInput, setViewAsInput] = useState("");
  // A token minted by hand for a script carries a role but no login; the console names it by the role alone.
  const role = claims?.role ?? "unknown";
  const line = claims?.login ? `@${claims.login} · ${role}` : role;
  return (
    <>
      <div className="signed-in" aria-label="Access">
        <span className="eyebrow">{viewingAs ? "Viewing as" : "Signed in as"}</span>
        <strong>{viewingAs ? `@${viewingAs} · streamer` : line}</strong>
        {viewingAs && <span className="signed-in-note">Signed in as {line}</span>}
        <span className="signed-in-actions">
          {viewingAs && (
            <button
              type="button"
              className="text-button"
              onClick={() => {
                stopViewAs();
                openHome();
              }}
            >
              Back to my view
            </button>
          )}
          <button
            type="button"
            className="text-button"
            onClick={() => {
              stopViewAs();
              clearAuthToken();
              reload();
            }}
          >
            Sign out
          </button>
        </span>
      </div>
      {operator && !viewingAs && (
        <form
          className="view-as"
          onSubmit={(event) => {
            event.preventDefault();
            if (startViewAs(viewAsInput)) openHome();
          }}
        >
          <label className="eyebrow" htmlFor="view-as-login">
            View as a streamer
          </label>
          <span className="view-as-row">
            <input
              id="view-as-login"
              className="text-input"
              placeholder="@channel"
              autoComplete="off"
              spellCheck={false}
              value={viewAsInput}
              onChange={(event) => setViewAsInput(event.target.value)}
            />
            <button type="submit" className="button-secondary button-sm" disabled={viewAsInput.trim() === ""}>
              View
            </button>
          </span>
        </form>
      )}
    </>
  );
}
