import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, apiFetch, apiSend, apiUrl } from "./api-client";

function jsonResponse(status: number, body: unknown, contentType = "application/json"): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: new Headers({ "content-type": contentType }),
    json: () => Promise.resolve(body),
  } as unknown as Response;
}

describe("api-client", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it("sends JSON with the bearer token from local storage and a timeout signal", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, { ok: true }));
    vi.stubGlobal("fetch", fetchMock);
    const storage = { getItem: vi.fn().mockReturnValue("demo-token") };

    const result = await apiFetch<{ ok: boolean }>("/api/chat/twitch/channels", {
      method: "POST",
      body: { channels: ["test"] },
      storage,
    });

    expect(result).toEqual({ ok: true });
    expect(storage.getItem).toHaveBeenCalledWith("streamsense.authToken");
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe("/api/chat/twitch/channels");
    expect(init.method).toBe("POST");
    expect(init.body).toBe(JSON.stringify({ channels: ["test"] }));
    expect(init.headers).toMatchObject({
      Accept: "application/json",
      "Content-Type": "application/json",
      Authorization: "Bearer demo-token",
    });
    expect(init.signal).toBeInstanceOf(AbortSignal);
  });

  it("omits the Authorization header and the body when there is no token and no payload", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, []));
    vi.stubGlobal("fetch", fetchMock);

    await apiFetch("/api/chat/twitch/status", { storage: { getItem: () => null } });

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(init.method).toBe("GET");
    expect(init.body).toBeUndefined();
    expect(init.headers).not.toHaveProperty("Authorization");
    expect(init.headers).not.toHaveProperty("Content-Type");
  });

  it("turns a problem+json response into an ApiError carrying the detail", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        jsonResponse(
          409,
          {
            type: "https://streamsense.dev/problems/conflict",
            title: "Conflict",
            status: 409,
            detail: "Twitch chat ingestion is disabled",
            service: "chat-service",
          },
          "application/problem+json",
        ),
      ),
    );

    const failure = await apiSend("/api/chat/twitch/channels", { body: { channels: ["test"] }, storage: null }).catch(
      (error: unknown) => error,
    );

    expect(failure).toBeInstanceOf(ApiError);
    const apiError = failure as ApiError;
    expect(apiError.status).toBe(409);
    expect(apiError.message).toBe("/api/chat/twitch/channels returned 409: Twitch chat ingestion is disabled");
    expect(apiError.problem?.service).toBe("chat-service");
  });

  it("still fails cleanly when the error response has no body or headers", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: false, status: 503 } as Response));

    await expect(apiFetch("/ml/segment", { storage: null })).rejects.toThrow("/ml/segment returned 503");
  });

  it("builds URLs with encoded query parameters", () => {
    expect(apiUrl("/api/sentiment/transcript/recent", { streamer: "red bull", limit: 10 })).toBe(
      "/api/sentiment/transcript/recent?streamer=red+bull&limit=10",
    );
    expect(apiUrl("/api/chat/twitch/status")).toBe("/api/chat/twitch/status");
  });
});
