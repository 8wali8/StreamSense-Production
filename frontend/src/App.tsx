import { BrowserRouter, Link, Route, Routes } from "react-router";
import { AppShell } from "./components/AppShell";
import { DealPage } from "./features/deals/DealPage";
import { HomePage } from "./features/home/HomePage";
import { OpsPage } from "./features/ops/OpsPage";
import { LatestSessionRedirect } from "./features/session/LatestSessionRedirect";
import {
  SessionMentionsPage,
  SessionRiskPage,
  SessionStreamPage,
  SessionValuePage,
} from "./features/session/SessionDetailPages";
import { SessionReportPage } from "./features/session/SessionReportPage";
import { StreamerProvider } from "./features/streamer/StreamerProvider";
import { captureShareToken, readShareToken } from "./lib/share-token";

function NotFound() {
  return (
    <div className="page">
      <header className="page-header">
        <div>
          <div className="eyebrow">Not found</div>
          <h1>There is no page here</h1>
          <p className="page-lede">
            <Link to="/">Back to home</Link>
          </p>
        </div>
      </header>
    </div>
  );
}

function SharedLanding() {
  return (
    <div className="page">
      <header className="page-header">
        <div>
          <div className="eyebrow">Shared report</div>
          <h1>Open the link you were sent</h1>
          <p className="page-lede">
            A share link opens one deal and the streams inside it. Nothing else is available here.
          </p>
        </div>
      </header>
    </div>
  );
}

/**
 * The routed application without a router, so tests can mount it under a MemoryRouter. A tab opened
 * from a share link gets only the deal and session pages; the home and operations pages are not routed.
 */
export function AppRoutes() {
  const shared = readShareToken() != null;
  return (
    <StreamerProvider>
      <Routes>
        <Route element={<AppShell />}>
          <Route index element={shared ? <SharedLanding /> : <HomePage />} />
          <Route path="sessions/latest" element={<LatestSessionRedirect />} />
          <Route path="sessions/:sessionId" element={<SessionReportPage />} />
          <Route path="sessions/:sessionId/value" element={<SessionValuePage />} />
          <Route path="sessions/:sessionId/mentions" element={<SessionMentionsPage />} />
          <Route path="sessions/:sessionId/risk" element={<SessionRiskPage />} />
          <Route path="sessions/:sessionId/stream" element={<SessionStreamPage />} />
          <Route path="deals/:dealId" element={<DealPage />} />
          {!shared && <Route path="ops" element={<OpsPage />} />}
          <Route path="*" element={<NotFound />} />
        </Route>
      </Routes>
    </StreamerProvider>
  );
}

export default function App() {
  // A share link carries its token in the URL; keep it for the tab before anything renders.
  captureShareToken(window.location.search);
  return (
    <BrowserRouter>
      <AppRoutes />
    </BrowserRouter>
  );
}
