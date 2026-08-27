import { beforeEach, describe, expect, it, vi } from "vitest"
import { buildConnectionParams, buildWsClientOptions, makeWsUrl } from "./client"

describe("apollo client websocket configuration", () => {
  beforeEach(() => {
    vi.useRealTimers()
  })

  it("builds ws urls for http pages", () => {
    expect(makeWsUrl({ protocol: "http:", host: "localhost:3000" })).toBe("ws://localhost:3000/graphql")
  })

  it("builds wss urls for https pages", () => {
    expect(makeWsUrl({ protocol: "https:", host: "demo.streamsense.dev" })).toBe("wss://demo.streamsense.dev/graphql")
  })

  it("omits auth header when no local token exists", () => {
    const storage = { getItem: vi.fn().mockReturnValue(null) }

    expect(buildConnectionParams(storage)).toEqual({})
  })

  it("forwards bearer token from local storage into websocket connection params", () => {
    const storage = { getItem: vi.fn().mockReturnValue("demo-token") }

    expect(buildConnectionParams(storage)).toEqual({ Authorization: "Bearer demo-token" })
  })

  it("uses keepalive and infinite retries for reconnect behavior", async () => {
    vi.useFakeTimers()
    const options = buildWsClientOptions({ protocol: "http:", host: "localhost:3000" }, { getItem: vi.fn().mockReturnValue(null) })

    expect(options.keepAlive).toBe(15000)
    expect(options.retryAttempts).toBe(Infinity)
    expect(options.shouldRetry?.(new Error("temporary disconnect"))).toBe(true)

    const retryPromise = options.retryWait?.(2)
    await vi.advanceTimersByTimeAsync(4000)
    await retryPromise
  })
})
