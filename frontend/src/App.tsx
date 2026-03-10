import { Health } from "./components/Health";
import { LiveChat } from "./pages/LiveChat";

export default function App() {
  return (
    <div>
      <div style={{ padding: 12, borderBottom: "1px solid #eee" }}>
        <Health />
      </div>
      <LiveChat />
    </div>
  );
}