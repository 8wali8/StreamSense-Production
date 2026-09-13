import { ApolloClient, gql, InMemoryCache } from "@apollo/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createDemoLink, pickEntry, type Snapshot } from "./demo-link";
import { isDemoPath } from "./mode";

const snapshot: Snapshot = {
  meta: { channel: "redbull-testing", sponsor: "Red Bull", dealId: "3", latestSessionId: "47" },
  graphql: {
    Sessions: [
      {
        variables: { streamer: "redbull-testing", limit: 50 },
        data: { sessions: [{ __typename: "StreamSession", id: "47", streamer: "redbull-testing", live: false }] },
      },
    ],
    SessionSummary: [
      { variables: { sessionId: "47", sponsor: null }, data: { sessionSummary: { __typename: "S", mentions: 1 } } },
      {
        variables: { sessionId: "47", sponsor: "Red Bull" },
        data: { sessionSummary: { __typename: "S", mentions: 9 } },
      },
    ],
    RecentSentiment: [
      {
        variables: { streamer: "redbull-testing", limit: 24 },
        data: {
          recentSentiment: [
            { __typename: "SentimentAnalysisEvent", sentimentEventId: "newest", message: "second" },
            { __typename: "SentimentAnalysisEvent", sentimentEventId: "oldest", message: "first" },
          ],
        },
      },
    ],
  },
  rest: {},
  chat: [],
};

describe("demo link", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("picks the recorded entry whose variables agree, ignoring the limit", () => {
    expect(pickEntry(snapshot.graphql.Sessions, { streamer: "redbull-testing", limit: 1 })?.variables.limit).toBe(50);
    expect(pickEntry(snapshot.graphql.SessionSummary, { sessionId: "47", sponsor: "Red Bull" })?.data).toEqual({
      sessionSummary: { __typename: "S", mentions: 9 },
    });
    expect(pickEntry(snapshot.graphql.SessionSummary, { sessionId: "47", sponsor: undefined })?.data).toEqual({
      sessionSummary: { __typename: "S", mentions: 1 },
    });
    expect(pickEntry(snapshot.graphql.SessionSummary, { sessionId: "48", sponsor: null })).toBeNull();
    expect(pickEntry(undefined, {})).toBeNull();
  });

  it("answers queries from the snapshot and refuses what it does not hold", async () => {
    const client = new ApolloClient({
      link: createDemoLink(() => Promise.resolve(snapshot)),
      cache: new InMemoryCache(),
    });
    const result = await client.query({
      query: gql`
        query Sessions($streamer: String!, $limit: Int) {
          sessions(streamer: $streamer, limit: $limit) {
            id
            streamer
            live
          }
        }
      `,
      variables: { streamer: "redbull-testing", limit: 1 },
    });
    expect(result.data).toEqual({
      sessions: [{ __typename: "StreamSession", id: "47", streamer: "redbull-testing", live: false }],
    });

    await expect(
      client.query({
        query: gql`
          query Deals($streamer: String) {
            deals(streamer: $streamer) {
              id
            }
          }
        `,
        variables: { streamer: "redbull-testing" },
      }),
    ).rejects.toThrow(/no data for Deals/);
  });

  it("replays a feed as a subscription, oldest event first", async () => {
    const client = new ApolloClient({
      link: createDemoLink(() => Promise.resolve(snapshot)),
      cache: new InMemoryCache(),
    });
    const seen: string[] = [];
    const subscription = client
      .subscribe({
        query: gql`
          subscription OnSentiment($streamer: String!) {
            onSentiment(streamer: $streamer) {
              sentimentEventId
              message
            }
          }
        `,
        variables: { streamer: "redbull-testing" },
      })
      .subscribe((event) => {
        const payload = event.data as { onSentiment: { sentimentEventId: string } };
        seen.push(payload.onSentiment.sentimentEventId);
      });
    await vi.advanceTimersByTimeAsync(4000 * 3 + 10);
    expect(seen).toEqual(["oldest", "newest", "oldest"]);
    subscription.unsubscribe();
  });

  it("knows which paths are the demo", () => {
    expect(isDemoPath("/demo")).toBe(true);
    expect(isDemoPath("/demo/sessions/47")).toBe(true);
    expect(isDemoPath("/demonstration")).toBe(false);
    expect(isDemoPath("/")).toBe(false);
  });
});
