import { type FormEvent, useEffect, useState } from "react";
import { fetchAuthProviders, twitchSignInUrl } from "../../api/auth";
import { authTokenFromInput } from "../../lib/auth-token";
import { DetectionHeader } from "./DetectionHeader";
import { describeSignInFailure } from "./sign-in-failure";

type Props = {
  /** The browser held a token, but it has run out. */
  expired: boolean;
  onSignedIn: (token: string) => void;
  /** Where a Twitch sign-in returns to; defaults to the page the browser is on. */
  returnPath?: string;
  /** The `signin` reason the gateway sent the browser back with, when a Twitch sign-in did not finish. */
  signInFailure?: string | null;
};

/**
 * The page in front of the console. Sign in with Twitch when the deployment offers it, and always the
 * access link the operator sends (`https://host/#token=<jwt>`), pasted whole or as its bare token.
 */
export function LoginPage({
  expired,
  onSignedIn,
  returnPath = `${window.location.pathname}${window.location.search}`,
  signInFailure = null,
}: Props) {
  const [input, setInput] = useState("");
  const [problem, setProblem] = useState<string | null>(null);
  const [twitch, setTwitch] = useState(false);

  useEffect(() => {
    let cancelled = false;
    fetchAuthProviders()
      .then((providers) => {
        if (!cancelled) setTwitch(providers.twitch);
      })
      .catch(() => {
        // No answer means no button; the access link still works.
      });
    return () => {
      cancelled = true;
    };
  }, []);

  function submit(event: FormEvent) {
    event.preventDefault();
    const token = authTokenFromInput(input);
    if (!token) {
      setProblem("That is not an access link or token.");
      return;
    }
    onSignedIn(token);
  }

  const failure = describeSignInFailure(signInFailure);

  return (
    <div className="login-page">
      <main className="login-card" aria-label="Sign in">
        <h1 className="visually-hidden">StreamSense</h1>
        <DetectionHeader />
        {failure && (
          <p className="status-line" role="alert">
            {failure}
          </p>
        )}
        {twitch && (
          <>
            <a className="button-twitch" href={twitchSignInUrl(returnPath)}>
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M4.5 2 2.5 6v14h5v3h3l3-3h4l5-5V2h-18zm16 12-3 3h-5l-3 3v-3h-4V4h15v10zm-3-7h-2v5h2V7zm-5 0h-2v5h2V7z" />
              </svg>
              Sign in with Twitch
            </a>
            <div className="login-divider">
              <span>or</span>
            </div>
          </>
        )}
        <p className="login-lede">
          {expired ? "Your access link has expired. Paste a new one below." : "Paste your access link or token below."}
        </p>
        <form onSubmit={submit}>
          <label className="field">
            <span className="field-label">Access link or token</span>
            <input
              className="text-input"
              value={input}
              onChange={(event) => setInput(event.target.value)}
              placeholder="https://…/#token=…"
              autoComplete="off"
              spellCheck={false}
            />
          </label>
          <div className="form-actions">
            <button className="button-primary" type="submit">
              Open the console
            </button>
          </div>
          {problem && (
            <div className="status-line" role="alert">
              {problem}
            </div>
          )}
        </form>
      </main>
    </div>
  );
}
