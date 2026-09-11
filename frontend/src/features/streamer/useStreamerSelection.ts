import { useState } from "react";
import { switchTwitchChannels } from "../../api/chat";
import { updateSponsorProfile } from "../../api/sentiment";
import { switchCaptureChannels } from "../../api/video";
import { currentAuthClaims } from "../../lib/auth-token";
import { normalizeStreamerHandle, sponsorProfileFromInput } from "./streamer";

export type StreamerSelection = {
  streamerInput: string;
  setStreamerInput: (value: string) => void;
  /** The channel the runtime is pointed at and every page shows. */
  selectedStreamer: string;
  sponsorBrand: string;
  setSponsorBrand: (value: string) => void;
  /** The brand shown in the shell and console when the input is blank. */
  displayBrand: string;
  /** Latest outcome of pointing the runtime (chat, capture, relevance) at the selected streamer. */
  channelSwitchStatus: string;
  /** Point chat ingest, video capture, and sponsor relevance at the streamer in the input. */
  pointRuntimeAtStreamer: () => Promise<void>;
};

const STORAGE_KEY = "streamsense.selection";
/** No sponsor until one is entered on the operations page; the pages then show "Sponsor" as a placeholder. */
const DEFAULT_SELECTION = { streamer: "test", sponsor: "" };

type StoredSelection = { streamer: string; sponsor: string };

/**
 * The channel to show: a streamer signed in with Twitch always sees their own (they cannot switch
 * channels anyway); an operator sees the last selection this browser made, or their own login when it
 * has none; without a Twitch sign-in the stored selection or the default applies.
 */
export function readStoredSelection(): StoredSelection {
  const claims = currentAuthClaims();
  const login = claims?.login ?? null;
  const stored = storedSelection();
  if (login && claims?.role === "streamer") {
    return { streamer: login, sponsor: stored?.sponsor ?? DEFAULT_SELECTION.sponsor };
  }
  if (stored) return stored;
  return login ? { streamer: login, sponsor: DEFAULT_SELECTION.sponsor } : DEFAULT_SELECTION;
}

/** The last selection this browser made, so a reload or a new tab shows the same channel. */
function storedSelection(): StoredSelection | null {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Partial<StoredSelection>;
    return {
      streamer: typeof parsed.streamer === "string" && parsed.streamer ? parsed.streamer : DEFAULT_SELECTION.streamer,
      sponsor: typeof parsed.sponsor === "string" ? parsed.sponsor : DEFAULT_SELECTION.sponsor,
    };
  } catch {
    return null;
  }
}

function storeSelection(selection: StoredSelection): void {
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(selection));
  } catch {
    // Storage can be unavailable (private mode, blocked site data); the selection still works for this page.
  }
}

/**
 * Which streamer and sponsor the console is reviewing, and the runtime updates a change triggers:
 * chat ingest and video capture follow the streamer, sponsor relevance follows the brand.
 * Set from the operations page; read by every other page through the streamer context.
 */
export function useStreamerSelection(): StreamerSelection {
  const [initial] = useState(readStoredSelection);
  const [streamerInput, setStreamerInput] = useState(initial.streamer);
  const [selectedStreamer, setSelectedStreamer] = useState(initial.streamer);
  const [sponsorBrand, setSponsorBrand] = useState(initial.sponsor);
  const [channelSwitchStatus, setChannelSwitchStatus] = useState("Runtime capture follows the streamer field.");

  async function pointRuntimeAtStreamer(): Promise<void> {
    const nextStreamer = normalizeStreamerHandle(streamerInput);
    if (!nextStreamer) return;
    const isSameStreamer = nextStreamer === selectedStreamer;
    setSelectedStreamer(nextStreamer);
    storeSelection({ streamer: nextStreamer, sponsor: sponsorBrand });
    setChannelSwitchStatus(
      isSameStreamer
        ? `Updating sponsor relevance for @${nextStreamer}...`
        : `Switching Twitch ingest to @${nextStreamer}...`,
    );

    const profile = sponsorProfileFromInput(nextStreamer, sponsorBrand);
    const relevanceUpdate = profile.sponsor ? [updateSponsorProfile(profile)] : [];
    const runtimeUpdates = isSameStreamer
      ? relevanceUpdate
      : [switchTwitchChannels([nextStreamer]), switchCaptureChannels([nextStreamer]), ...relevanceUpdate];

    const results = await Promise.allSettled(runtimeUpdates);
    const failures = results.filter((result) => result.status === "rejected");
    if (failures.length > 0) {
      setChannelSwitchStatus(
        `Loaded @${nextStreamer}; ${failures.length} runtime update failed. Check the pipeline status above.`,
      );
      return;
    }

    setChannelSwitchStatus(
      isSameStreamer
        ? `Sponsor relevance updated for @${nextStreamer}; existing capture was left running.`
        : `Chat, video frames, transcript capture, and sponsor relevance are pointed at @${nextStreamer}.`,
    );
  }

  const displayBrand = sponsorBrand.trim() || "Sponsor";

  return {
    streamerInput,
    setStreamerInput,
    selectedStreamer,
    sponsorBrand,
    setSponsorBrand,
    displayBrand,
    channelSwitchStatus,
    pointRuntimeAtStreamer,
  };
}
