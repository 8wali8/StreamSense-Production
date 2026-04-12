import { Health } from "./components/Health";
import { RecommendationPanel } from "./components/RecommendationPanel";
import { SponsorPanel } from "./components/SponsorPanel";
import { SentimentPanel } from "./components/SentimentPanel";
import { LiveChat } from "./pages/LiveChat";

export default function App() {
  return (
    <div style={{ minHeight: "100vh", background: "#f3f7fb", color: "#0f172a" }}>
      <div style={{ padding: 12, borderBottom: "1px solid #d9e1ec", background: "white" }}>
        <div style={{ maxWidth: 1200, margin: "0 auto" }}>
          <Health />
        </div>
      </div>

      <div
        style={{
          maxWidth: 1200,
          margin: "0 auto",
          padding: 16,
          display: "grid",
          gap: 16,
          gridTemplateColumns: "repeat(auto-fit, minmax(320px, 1fr))",
        }}
        >
          <LiveChat />
          <SentimentPanel />
          <SponsorPanel />
          <RecommendationPanel />
        </div>
      </div>
    );
}
