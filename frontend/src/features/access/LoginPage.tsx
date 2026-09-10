import { type FormEvent, useState } from "react";
import { authTokenFromInput } from "../../lib/auth-token";

type Props = {
  /** The browser held a token, but it has run out. */
  expired: boolean;
  onSignedIn: (token: string) => void;
};

/**
 * The page in front of the console. Until there is a real identity provider, the credential is the
 * access link the operator sends (`https://host/#token=<jwt>`), pasted whole or as its bare token.
 * Sign in with Twitch lands on this same page later.
 */
export function LoginPage({ expired, onSignedIn }: Props) {
  const [input, setInput] = useState("");
  const [problem, setProblem] = useState<string | null>(null);

  function submit(event: FormEvent) {
    event.preventDefault();
    const token = authTokenFromInput(input);
    if (!token) {
      setProblem("That is not an access link or token.");
      return;
    }
    onSignedIn(token);
  }

  return (
    <div className="login-page">
      <main className="login-card" aria-label="Sign in">
        <div className="brand-lockup">
          <div className="brand-mark">SS</div>
          <div>
            <div className="brand-name">StreamSense</div>
            <div className="brand-kicker">Sponsorship proof</div>
          </div>
        </div>
        <div>
          <h1>{expired ? "Your access link has expired" : "Sign in to StreamSense"}</h1>
          <p className="page-lede">
            Paste the access link you were sent, or the token from it. Ask whoever runs this console for a new one if
            yours has run out.
          </p>
        </div>
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
