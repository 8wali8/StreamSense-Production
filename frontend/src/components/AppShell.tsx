import { NavLink, Outlet } from "react-router";
import { useStreamer } from "../features/streamer/streamer-context";
import { readShareToken } from "../lib/share-token";

function navClass({ isActive }: { isActive: boolean }): string {
  return isActive ? "nav-item nav-item-active" : "nav-item";
}

/** Left navigation plus the routed page. The operations link sits at the bottom, away from customer pages. */
export function AppShell() {
  const { selectedStreamer } = useStreamer();
  const shared = readShareToken() != null;

  if (shared) {
    return (
      <div className="app-shell">
        <aside className="sidebar" aria-label="Shared view">
          <div className="brand-lockup">
            <div className="brand-mark">SS</div>
            <div>
              <div className="brand-name">StreamSense</div>
              <div className="brand-kicker">Shared report</div>
            </div>
          </div>
          <div className="sidebar-footer">
            <div className="signed-in">
              <span className="eyebrow">Read-only</span>
              <strong>Shared by the streamer</strong>
            </div>
          </div>
        </aside>
        <main className="main-stage">
          <Outlet />
        </main>
      </div>
    );
  }

  return (
    <div className="app-shell">
      <aside className="sidebar" aria-label="Primary navigation">
        <div className="brand-lockup">
          <div className="brand-mark">SS</div>
          <div>
            <div className="brand-name">StreamSense</div>
            <div className="brand-kicker">Sponsorship proof</div>
          </div>
        </div>

        <nav className="nav-stack">
          <NavLink className={navClass} to="/" end>
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M3 11l9-8 9 8" />
              <path d="M5 10v10h14V10" />
            </svg>
            Home
          </NavLink>
          <NavLink className={navClass} to="/sessions/latest">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M6 3h9l4 4v14H6z" />
              <path d="M9 12h7M9 16h7" />
            </svg>
            Session report
          </NavLink>
        </nav>

        <div className="sidebar-footer">
          <div className="signed-in">
            <span className="eyebrow">Channel</span>
            <strong>@{selectedStreamer}</strong>
          </div>
          <NavLink className={navClass} to="/ops">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <circle cx="12" cy="12" r="3" />
              <path d="M12 2v3M12 19v3M2 12h3M19 12h3M4.9 4.9l2.1 2.1M17 17l2.1 2.1M4.9 19.1L7 17M17 7l2.1-2.1" />
            </svg>
            Operations
          </NavLink>
        </div>
      </aside>

      <main className="main-stage">
        <Outlet />
      </main>
    </div>
  );
}
