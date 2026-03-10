import { useRef, useState } from "react";
import { useSubscription } from "@apollo/client/react";
import { ON_CHAT_MESSAGE_SUBSCRIPTION } from "../graphql/subscriptions";

type ChatMessageEvent = {
    eventId: string;
    streamer: string;
    user: string;
    message: string;
    timestamp: number; // epoch millis
};

type OnChatMessageData = {
    onChatMessage: ChatMessageEvent;
};

function formatTime(ts: number): string {
    return new Date(ts).toLocaleTimeString();
}

export function LiveChat() {
    const [streamerInput, setStreamerInput] = useState("test");
    const [activeStreamer, setActiveStreamer] = useState<string>("");
    const [connected, setConnected] = useState(false);

    const [events, setEvents] = useState<ChatMessageEvent[]>([]);
    const seenIdsRef = useRef<Set<string>>(new Set());

    const { error } = useSubscription<OnChatMessageData>(ON_CHAT_MESSAGE_SUBSCRIPTION, {
        variables: { streamer: activeStreamer },
        skip: !connected || !activeStreamer,

        // ✅ this replaces useEffect + fixes the lint error
        onData: ({ data }) => {
            const evt = data.data?.onChatMessage;
            if (!evt) return;

            if (seenIdsRef.current.has(evt.eventId)) return;
            seenIdsRef.current.add(evt.eventId);

            setEvents((prev) => {
                const next = [...prev, evt];
                return next.length > 200 ? next.slice(next.length - 200) : next;
            });
        },
    });

    function onConnect() {
        const s = streamerInput.trim();
        if (!s) return;

        setActiveStreamer(s);
        setEvents([]);
        seenIdsRef.current.clear();
        setConnected(true);
    }

    function onDisconnect() {
        setConnected(false);
    }

    return (
        <div style={{ padding: 16, maxWidth: 900, margin: "0 auto", fontFamily: "system-ui" }}>
            <h1 style={{ marginBottom: 8 }}>Live Chat</h1>

            <div style={{ display: "flex", gap: 8, alignItems: "center", marginBottom: 12 }}>
                <label style={{ display: "flex", flexDirection: "column", gap: 4 }}>
                    <span style={{ fontSize: 12, opacity: 0.7 }}>Streamer</span>
                    <input
                        value={streamerInput}
                        onChange={(e) => setStreamerInput(e.target.value)}
                        placeholder="e.g. test"
                        style={{ padding: 8, width: 220 }}
                    />
                </label>

                {!connected ? (
                    <button onClick={onConnect} style={{ padding: "10px 14px", cursor: "pointer" }}>
                        Connect
                    </button>
                ) : (
                    <button onClick={onDisconnect} style={{ padding: "10px 14px", cursor: "pointer" }}>
                        Disconnect
                    </button>
                )}

                <div style={{ marginLeft: "auto", fontSize: 12, opacity: 0.8 }}>
                    Status:{" "}
                    {!connected ? "disconnected" : error ? `error (${error.message})` : `listening (streamer=${activeStreamer})`}
                </div>
            </div>

            <div style={{ border: "1px solid #ddd", borderRadius: 8, padding: 12 }}>
                <div style={{ fontSize: 12, opacity: 0.7, marginBottom: 8 }}>
                    Showing last {events.length} messages
                </div>

                <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
                    {events.map((e) => (
                        <div key={e.eventId} style={{ borderBottom: "1px solid #f0f0f0", paddingBottom: 8 }}>
                            <div style={{ fontSize: 12, opacity: 0.7 }}>
                                [{formatTime(e.timestamp)}] {e.streamer} • eventId={e.eventId}
                            </div>
                            <div style={{ fontWeight: 600 }}>{e.user}</div>
                            <div>{e.message}</div>
                        </div>
                    ))}

                    {events.length === 0 && (
                        <div style={{ opacity: 0.7 }}>
                            {connected ? "No messages yet — ingest some events." : "Connect to start receiving events."}
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}