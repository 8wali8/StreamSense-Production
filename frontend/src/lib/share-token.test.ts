import { describe, expect, it } from "vitest";
import { captureShareToken, shareHeaders, shareTokenFromSearch, shareUrl } from "./share-token";

function memoryStorage(initial: Record<string, string> = {}) {
  const items = { ...initial };
  return {
    getItem: (key: string) => items[key] ?? null,
    setItem: (key: string, value: string) => {
      items[key] = value;
    },
  };
}

describe("share-token", () => {
  it("reads the token from the link and keeps it for the tab", () => {
    const storage = memoryStorage();
    expect(shareTokenFromSearch("?share=abc%20d&x=1")).toBe("abc d");
    expect(shareTokenFromSearch("?x=1")).toBeNull();
    expect(shareHeaders(memoryStorage())).toEqual({});
    expect(captureShareToken("?share=tok-1", storage)).toBe("tok-1");
    expect(captureShareToken("", storage)).toBe("tok-1");
    expect(shareHeaders(storage)).toEqual({ "X-StreamSense-Share": "tok-1" });
  });

  it("keeps the token for this page load when session storage refuses it", () => {
    const refuse = () => {
      throw new Error("storage is blocked");
    };
    const storage = { getItem: refuse, setItem: refuse };
    expect(captureShareToken("?share=tok-2", storage)).toBe("tok-2");
    expect(shareHeaders(storage)).toEqual({ "X-StreamSense-Share": "tok-2" });
  });

  it("builds the link a streamer sends", () => {
    expect(shareUrl("3", "a/b", "https://streamsense.example")).toBe("https://streamsense.example/deals/3?share=a%2Fb");
  });
});
