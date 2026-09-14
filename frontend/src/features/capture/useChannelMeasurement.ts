import { useCallback, useState } from "react";
import { getTwitchChannelIngest, joinTwitchChannel, partTwitchChannel } from "../../api/chat";
import { getCaptureChannel, startCaptureChannel, stopCaptureChannel } from "../../api/video";
import { usePolledResource } from "../../hooks/usePolledResource";
import { describeError } from "../../lib/errors";
import { describeMeasurement, type Measurement } from "./measurement";

const POLL_MS = 30_000;

export type ChannelMeasurement = {
  measurement: Measurement;
  /** True until both reads have answered once for this channel. */
  loading: boolean;
  /** A start or stop is in flight. */
  pending: boolean;
  /** Why the last start or stop did not do everything it was asked to, or a failed read. */
  error: string | null;
  start: () => Promise<void>;
  stop: () => Promise<void>;
};

/**
 * Whether one channel is being measured, and the two calls that start and stop it. Chat ingest and
 * video capture are separate services, so both are asked and a half-failure says which half failed
 * rather than claiming the channel is off.
 */
export function useChannelMeasurement(channel: string): ChannelMeasurement {
  const chat = usePolledResource(() => getTwitchChannelIngest(channel), POLL_MS, channel);
  const capture = usePolledResource(() => getCaptureChannel(channel), POLL_MS, channel);
  const [pending, setPending] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  const refresh = capture.refresh;
  const refreshChat = chat.refresh;

  const run = useCallback(
    async (verb: string, chatCall: () => Promise<unknown>, captureCall: () => Promise<unknown>) => {
      setPending(true);
      setActionError(null);
      const [chatResult, captureResult] = await Promise.allSettled([chatCall(), captureCall()]);
      const problems: string[] = [];
      if (chatResult.status === "rejected") {
        problems.push(`chat ingest (${describeError(asError(chatResult.reason))})`);
      }
      if (captureResult.status === "rejected") {
        problems.push(`video capture (${describeError(asError(captureResult.reason))})`);
      }
      setActionError(problems.length === 0 ? null : `Could not ${verb} ${problems.join(" or ")}.`);
      refreshChat();
      refresh();
      setPending(false);
    },
    [refresh, refreshChat],
  );

  const start = useCallback(
    () =>
      run(
        "start",
        () => joinTwitchChannel(channel),
        () => startCaptureChannel(channel),
      ),
    [channel, run],
  );
  const stop = useCallback(
    () =>
      run(
        "stop",
        () => partTwitchChannel(channel),
        () => stopCaptureChannel(channel),
      ),
    [channel, run],
  );

  return {
    measurement: describeMeasurement(chat.data, capture.data),
    loading: chat.data === null && capture.data === null && chat.error === null && capture.error === null,
    pending,
    error: actionError ?? chat.error ?? capture.error,
    start,
    stop,
  };
}

function asError(reason: unknown): Error {
  return reason instanceof Error ? reason : new Error("unknown error");
}
