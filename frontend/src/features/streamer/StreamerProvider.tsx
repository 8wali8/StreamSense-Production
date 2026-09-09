import type { ReactNode } from "react";
import { StreamerContext } from "./streamer-context";
import { useStreamerSelection } from "./useStreamerSelection";

export function StreamerProvider({ children }: { children: ReactNode }) {
  const selection = useStreamerSelection();
  return <StreamerContext.Provider value={selection}>{children}</StreamerContext.Provider>;
}
