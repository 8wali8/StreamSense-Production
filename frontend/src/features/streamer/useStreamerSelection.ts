import { useState } from "react";
import { switchTwitchChannels } from "../../api/chat";
import { updateSponsorProfile } from "../../api/sentiment";
import { switchCaptureChannels } from "../../api/video";
import { normalizeStreamerHandle, portfolioStreamers, sponsorProfileFromInput, type PortfolioStreamer } from "./streamer";

export type StreamerSelection = {
  streamerInput: string;
  setStreamerInput: (value: string) => void;
  selectedStreamer: string;
  sponsorBrand: string;
  setSponsorBrand: (value: string) => void;
  campaignGoal: string;
  setCampaignGoal: (value: string) => void;
  /** The brand shown in the sidebar and console when the input is blank. */
  displayBrand: string;
  /** Latest outcome of pointing the runtime (chat, capture, relevance) at the selected streamer. */
  channelSwitchStatus: string;
  analyzeStreamer: () => Promise<void>;
  selectPortfolioStreamer: (streamer: PortfolioStreamer) => Promise<void>;
};

/**
 * Which streamer and sponsor the console is reviewing, and the runtime updates a change triggers:
 * chat ingest and video capture follow the streamer, sponsor relevance follows the brand.
 */
export function useStreamerSelection(): StreamerSelection {
  const [streamerInput, setStreamerInput] = useState("test");
  const [selectedStreamer, setSelectedStreamer] = useState("test");
  const [sponsorBrand, setSponsorBrand] = useState("Nike");
  const [campaignGoal, setCampaignGoal] = useState("Launch-week brand lift");
  const [channelSwitchStatus, setChannelSwitchStatus] = useState("Runtime capture follows the streamer field.");

  async function loadStreamer(streamer: string, brand: string): Promise<void> {
    const nextStreamer = normalizeStreamerHandle(streamer);
    const isSameStreamer = nextStreamer === selectedStreamer;
    setSelectedStreamer(nextStreamer);
    setSponsorBrand(brand);
    setChannelSwitchStatus(
      isSameStreamer ? `Updating sponsor relevance for @${nextStreamer}...` : `Switching Twitch ingest to @${nextStreamer}...`,
    );

    const profile = sponsorProfileFromInput(nextStreamer, brand, campaignGoal);
    const relevanceUpdate = profile.sponsor ? [updateSponsorProfile(profile)] : [];
    const runtimeUpdates = isSameStreamer
      ? relevanceUpdate
      : [switchTwitchChannels([nextStreamer]), switchCaptureChannels([nextStreamer]), ...relevanceUpdate];

    const results = await Promise.allSettled(runtimeUpdates);
    const failures = results.filter((result) => result.status === "rejected");
    if (failures.length > 0) {
      setChannelSwitchStatus(`Loaded @${nextStreamer}; ${failures.length} runtime update failed. Check service status pills.`);
      return;
    }

    setChannelSwitchStatus(
      isSameStreamer
        ? `Sponsor relevance updated for @${nextStreamer}; existing replay capture was left running.`
        : `Chat, video frames, transcript capture, and sponsor relevance are pointed at @${nextStreamer}.`,
    );
  }

  async function analyzeStreamer(): Promise<void> {
    const nextStreamer = normalizeStreamerHandle(streamerInput);
    if (!nextStreamer) return;
    await loadStreamer(nextStreamer, sponsorBrand);
  }

  async function selectPortfolioStreamer(streamer: PortfolioStreamer): Promise<void> {
    const nextStreamer = normalizeStreamerHandle(streamer.handle);
    setStreamerInput(nextStreamer);
    setSponsorBrand(streamer.brand);
    await loadStreamer(nextStreamer, streamer.brand);
  }

  const selectedPortfolio = portfolioStreamers.find((streamer) => streamer.handle === selectedStreamer);
  const displayBrand = sponsorBrand.trim() || selectedPortfolio?.brand || "Sponsor";

  return {
    streamerInput,
    setStreamerInput,
    selectedStreamer,
    sponsorBrand,
    setSponsorBrand,
    campaignGoal,
    setCampaignGoal,
    displayBrand,
    channelSwitchStatus,
    analyzeStreamer,
    selectPortfolioStreamer,
  };
}
