import { useRef, useState } from "react";
import { useSubscription } from "@apollo/client/react";
import type {
  OnChatMessageSubscription,
  OnChatMessageSubscriptionVariables,
} from "../graphql/generated";
import { ON_CHAT_MESSAGE_SUBSCRIPTION } from "../graphql/subscriptions";

type ChatMessageEvent = OnChatMessageSubscription["onChatMessage"];

function formatTime(ts: number): string {
    return new Date(ts).toLocaleTimeString();
}

type LiveChatProps = {
    streamer?: string;
    autoConnect?: boolean;
    hideControls?: boolean;
};

export function LiveChat({ streamer, autoConnect = false, hideControls = false }: LiveChatProps) {
    const [streamerInput, setStreamerInput] = useState(streamer ?? "test");
    const [activeStreamer, setActiveStreamer] = useState<string>(autoConnect && streamer ? streamer : "");
    const [connected, setConnected] = useState(Boolean(autoConnect && streamer));

    const [events, setEvents] = useState<ChatMessageEvent[]>([]);
    const seenIdsRef = useRef<Set<string>>(new Set());

    const { error } = useSubscription<OnChatMessageSubscription, OnChatMessageSubscriptionVariables>(ON_CHAT_MESSAGE_SUBSCRIPTION, {
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

    const statusText = !connected ? "disconnected" : error ? `error (${error.message})` : `listening (streamer=${activeStreamer})`;

    return (
        <section className="dashboard-panel" id="evidence">
            <div className="panel-title-row panel-heading">
                <div>
                    <div className="eyebrow">Audience evidence</div>
                    <h2>Live Chat</h2>
                    <p>Raw audience messages that feed the sentiment pipeline.</p>
                </div>
                <span className="status-pill">{events.length} messages</span>
            </div>

            {!hideControls && (
                <div className="panel-actions">
                    <label>
                        <span className="field-label">Streamer</span>
                        <input
                            className="text-input"
                            value={streamerInput}
                            onChange={(e) => setStreamerInput(e.target.value)}
                            placeholder="e.g. test"
                        />
                    </label>

                    {!connected ? (
                        <button className="button-primary" onClick={onConnect}>Connect</button>
                    ) : (
                        <button className="button-secondary" onClick={onDisconnect}>Disconnect</button>
                    )}
                </div>
            )}

            <div className="status-line">Status: {statusText}</div>

            <div className="event-list">
                {events.map((e) => (
                    <article className="event-card" key={e.eventId}>
                        <div className="event-card-header">
                            <span className="event-meta">[{formatTime(e.timestamp)}] {e.streamer} • eventId={e.eventId}</span>
                            <span className="tag">chat</span>
                        </div>
                        <strong>{e.user}</strong>
                        <p>{e.message}</p>
                    </article>
                ))}

                {events.length === 0 && (
                    <div className="empty-state">
                        {connected ? "No messages yet — ingest some events." : "Connect to start receiving events."}
                    </div>
                )}
            </div>
        </section>
    );
}
