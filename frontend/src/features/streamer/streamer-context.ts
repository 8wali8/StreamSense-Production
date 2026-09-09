import { createContext, useContext } from "react";
import type { StreamerSelection } from "./useStreamerSelection";

export const StreamerContext = createContext<StreamerSelection | null>(null);

/** The streamer and sponsor every page shows. Provided once by `StreamerProvider` in the app shell. */
export function useStreamer(): StreamerSelection {
  const selection = useContext(StreamerContext);
  if (!selection) {
    throw new Error("useStreamer must be used inside StreamerProvider");
  }
  return selection;
}
