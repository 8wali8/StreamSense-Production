import { type ReactNode, useState } from "react";
import { isUsableToken, readAuthToken } from "../../lib/auth-token";
import { readShareToken } from "../../lib/share-token";
import { LoginPage } from "./LoginPage";

type Props = {
  children: ReactNode;
  now?: () => Date;
};

/**
 * The reason a Twitch sign-in came back with (`/?signin=<reason>`), taken out of the address bar so a
 * reload does not repeat it. Read once per page load.
 */
function takeSignInFailure(win: Pick<Window, "location" | "history"> = window): string | null {
  const params = new URLSearchParams(win.location.search);
  const reason = params.get("signin");
  if (reason === null) return null;
  params.delete("signin");
  const search = params.toString();
  win.history.replaceState(win.history.state, "", `${win.location.pathname}${search ? `?${search}` : ""}`);
  return reason;
}

/**
 * Nothing behind it renders until the browser holds a bearer token that has not run out. The token
 * arrives with the page: a Twitch sign-in comes back with it in the URL fragment, which App captures
 * before this renders. A tab opened from a share link passes: it authenticates with the share token
 * and gets the reduced shell instead.
 */
export function AccessGate({ children, now }: Props) {
  const [token] = useState(() => readAuthToken());
  const [signInFailure] = useState(() => takeSignInFailure());

  if (readShareToken() != null || isUsableToken(token, now)) {
    return <>{children}</>;
  }
  return <LoginPage expired={token != null} signInFailure={signInFailure} />;
}
