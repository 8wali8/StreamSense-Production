import { BrowserRouter, Link, Route, Routes } from "react-router";
import { AppShell } from "./components/AppShell";
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

/** The routed application without a router, so tests can mount it under a MemoryRouter. */
export function AppRoutes() {
  return (
    <StreamerProvider>
      <Routes>
        <Route element={<AppShell />}>
          <Route index element={<HomePage />} />
          <Route path="sessions/latest" element={<LatestSessionRedirect />} />
          <Route path="sessions/:sessionId" element={<SessionReportPage />} />
          <Route path="sessions/:sessionId/value" element={<SessionValuePage />} />
          <Route path="sessions/:sessionId/mentions" element={<SessionMentionsPage />} />
          <Route path="sessions/:sessionId/risk" element={<SessionRiskPage />} />
          <Route path="sessions/:sessionId/stream" element={<SessionStreamPage />} />
          <Route path="ops" element={<OpsPage />} />
          <Route path="*" element={<NotFound />} />
        </Route>
      </Routes>
    </StreamerProvider>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <AppRoutes />
    </BrowserRouter>
  );
}
