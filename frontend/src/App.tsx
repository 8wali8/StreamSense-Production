import { BrowserRouter, Link, Route, Routes } from "react-router";
import { AppShell } from "./components/AppShell";
import { HomePage } from "./features/home/HomePage";
import { OpsPage } from "./features/ops/OpsPage";
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
