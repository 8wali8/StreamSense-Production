import { type ReactNode, useState } from "react";
import { isUsableToken, readAuthToken, storeAuthToken } from "../../lib/auth-token";
import { readShareToken } from "../../lib/share-token";
import { LoginPage } from "./LoginPage";

type Props = {
  children: ReactNode;
  now?: () => Date;
};

/**
 * Nothing behind it renders until the browser holds a bearer token that has not run out. A tab opened
 * from a share link passes: it authenticates with the share token and gets the reduced shell instead.
 * Signing in only updates state; every transport reads the token per request, so no reload is needed.
 */
export function AccessGate({ children, now }: Props) {
  const [token, setToken] = useState(() => readAuthToken());

  if (readShareToken() != null || isUsableToken(token, now)) {
    return <>{children}</>;
  }
  return (
    <LoginPage
      expired={token != null}
      onSignedIn={(signedIn) => {
        storeAuthToken(signedIn);
        setToken(signedIn);
      }}
    />
  );
}
