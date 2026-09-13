import React from "react";
import ReactDOM from "react-dom/client";
import { ApolloProvider } from "@apollo/client/react";
import { apolloClient } from "./apollo/client";
import App from "./App";
import { createDemoClient } from "./demo/demo-link";
import { isDemoMode } from "./demo/mode";
import "@fontsource/ibm-plex-sans/400.css";
import "@fontsource/ibm-plex-sans/500.css";
import "@fontsource/ibm-plex-sans/600.css";
import "@fontsource/ibm-plex-mono/400.css";
import "@fontsource/ibm-plex-mono/500.css";
import "./index.css";

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    {/* Under /demo the console runs on the sealed snapshot and never opens a socket or a request to the gateway. */}
    <ApolloProvider client={isDemoMode() ? createDemoClient() : apolloClient}>
      <App />
    </ApolloProvider>
  </React.StrictMode>,
);
