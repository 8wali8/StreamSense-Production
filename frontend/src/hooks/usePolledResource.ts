import { useEffect, useRef, useState } from "react";
import { describeError } from "../lib/errors";

export type PolledResource<T> = {
  /** Latest successful result for the current key, or null before the first load (and after a key change). */
  data: T | null;
  /** Message of the latest failure; cleared by the next success. Stale data stays visible meanwhile. */
  error: string | null;
  refresh: () => void;
};

type Snapshot<T> = { key: string; data: T | null; error: string | null };

/**
 * Loads a resource now and every `intervalMs`, restarting when `key` changes (for example the
 * selected streamer). Results are tagged with the key they were loaded for, so a slow response for
 * a previous key never shows up under the current one.
 */
export function usePolledResource<T>(load: () => Promise<T>, intervalMs: number, key = ""): PolledResource<T> {
  const loadRef = useRef(load);
  const [snapshot, setSnapshot] = useState<Snapshot<T>>({ key, data: null, error: null });
  const [generation, setGeneration] = useState(0);

  useEffect(() => {
    loadRef.current = load;
  }, [load]);

  useEffect(() => {
    let cancelled = false;

    const run = async () => {
      try {
        const data = await loadRef.current();
        if (!cancelled) {
          setSnapshot({ key, data, error: null });
        }
      } catch (err) {
        if (!cancelled) {
          const message = describeError(err instanceof Error ? err : new Error("unknown error"));
          setSnapshot((previous) => ({ key, data: previous.key === key ? previous.data : null, error: message }));
        }
      }
    };

    void run();
    const intervalId = window.setInterval(() => void run(), intervalMs);
    return () => {
      cancelled = true;
      window.clearInterval(intervalId);
    };
  }, [intervalMs, key, generation]);

  const current = snapshot.key === key ? snapshot : { key, data: null, error: null };
  return { data: current.data, error: current.error, refresh: () => setGeneration((value) => value + 1) };
}
