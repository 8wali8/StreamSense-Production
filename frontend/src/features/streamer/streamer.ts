import type { SponsorProfile } from "../../api/sentiment";

export type PortfolioStreamer = {
  handle: string;
  brand: string;
  owner: string;
  risk: string;
};

export const portfolioStreamers: PortfolioStreamer[] = [
  { handle: "test", brand: "Nike", owner: "Demo channel", risk: "Low" },
  { handle: "speedrun-lab", brand: "Prime", owner: "Speedrun lab", risk: "Watch" },
  { handle: "arena-night", brand: "Razer", owner: "Arena night", risk: "Medium" },
];

/** Replay aliases (see config-repo chat-service.yml) whose player should show the VOD, not a live channel. */
const replayVodByStreamer: Record<string, string> = {
  "redbull-testing": "2750461300",
};

export function normalizeStreamerHandle(streamer: string): string {
  return streamer.trim().toLowerCase().replace(/^[@#]+/, "");
}

export function twitchPlayerUrl(channel: string, hostname: string = window.location.hostname): string {
  const parent = encodeURIComponent(hostname || "localhost");
  const replayVodId = replayVodByStreamer[channel];
  if (replayVodId) {
    return `https://player.twitch.tv/?video=${encodeURIComponent(replayVodId)}&parent=${parent}&muted=true&autoplay=true`;
  }
  return `https://player.twitch.tv/?channel=${encodeURIComponent(channel)}&parent=${parent}&muted=true&autoplay=true`;
}

/** "Red Bull, redbull, energy drink" becomes sponsor "Red Bull" with the rest as semantic terms. */
export function sponsorProfileFromInput(streamer: string, sponsorInput: string, campaignGoal: string): SponsorProfile {
  const parts = sponsorInput.split(",").map((part) => part.trim()).filter(Boolean);
  return {
    streamer: normalizeStreamerHandle(streamer),
    sponsor: parts[0] || sponsorInput.trim(),
    aliases: [],
    semanticTerms: parts.slice(1),
    campaignGoal,
  };
}
