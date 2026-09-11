import { type ReactNode, useState } from "react";
import { isUsableToken, readAuthToken, storeAuthToken } from "../../lib/auth-token";
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
 * Nothing behind it renders until the browser holds a bearer token that has not run out. A tab opened
 * from a share link passes: it authenticates with the share token and gets the reduced shell instead.
 * Signing in only updates state; every transport reads the token per request, so no reload is needed.
 */
export function AccessGate({ children, now }: Props) {
  const [token, setToken] = useState(() => readAuthToken());
  const [signInFailure] = useState(() => takeSignInFailure());

  if (readShareToken() != null || isUsableToken(token, now)) {
    return <>{children}</>;
  }
  return (
    <LoginPage
      expired={token != null}
      signInFailure={signInFailure}
      onSignedIn={(signedIn) => {
        storeAuthToken(signedIn);
        setToken(signedIn);
      }}
    />
  );
}
