import { useEffect, useState } from "react";
import { fetchAuthProviders, twitchSignInUrl } from "../../api/auth";
import { DEMO_BASE } from "../../demo/mode";
import { DetectionHeader } from "./DetectionHeader";
import { describeSignInFailure } from "./sign-in-failure";

type Props = {
  /** The browser held a token, but it has run out. */
  expired: boolean;
  /** Where a Twitch sign-in returns to; defaults to the page the browser is on. */
  returnPath?: string;
  /** The `signin` reason the gateway sent the browser back with, when a Twitch sign-in did not finish. */
  signInFailure?: string | null;
};

/** What `GET /auth/providers` said, once it has answered. */
type Providers = "twitch" | "none" | "unreachable";

/**
 * The page in front of the console: sign in with Twitch, or take a look at the demo. There is no other
 * way in; when the deployment has Twitch sign-in off, the page says so rather than offering a field.
 */
export function LoginPage({
  expired,
  returnPath = `${window.location.pathname}${window.location.search}`,
  signInFailure = null,
}: Props) {
  const [providers, setProviders] = useState<Providers | null>(null);

  useEffect(() => {
    let cancelled = false;
    fetchAuthProviders()
      .then((answer) => {
        if (!cancelled) setProviders(answer.twitch ? "twitch" : "none");
      })
      .catch(() => {
        if (!cancelled) setProviders("unreachable");
      });
    return () => {
      cancelled = true;
    };
  }, []);

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
        {expired && <p className="login-lede">Your sign-in has run out. Sign in again to carry on.</p>}
        {providers === "twitch" && (
          <a className="button-twitch" href={twitchSignInUrl(returnPath)}>
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M4.5 2 2.5 6v14h5v3h3l3-3h4l5-5V2h-18zm16 12-3 3h-5l-3 3v-3h-4V4h15v10zm-3-7h-2v5h2V7zm-5 0h-2v5h2V7z" />
            </svg>
            Sign in with Twitch
          </a>
        )}
        {providers === "none" && <p className="login-lede">Sign in with Twitch is not switched on for this console.</p>}
        {providers === "unreachable" && (
          <p className="login-lede">The sign-in service did not answer. Reload the page to try again.</p>
        )}
        <p className="login-demo">
          Don&rsquo;t have an account? <a href={DEMO_BASE}>Take a look at the demo</a>.
        </p>
      </main>
    </div>
  );
}
