import { screen, waitFor } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { renderWithApollo } from "../../test/apollo";
import { sessionSummary } from "../../test/fixtures";
import { graphqlResolver, HttpResponse, server } from "../../test/msw";
import { useSessionSummaries } from "./useSessionSummaries";

function Probe({ sponsor }: { sponsor: string }) {
  const { summaries, loading } = useSessionSummaries(["7"], sponsor);
  return (
    <div>
      <span data-testid="mentions">{summaries["7"]?.mentions ?? "none"}</span>
      <span data-testid="loading">{String(loading)}</span>
    </div>
  );
}

describe("useSessionSummaries", () => {
  it("does not show one sponsor's summaries under another's label while the new ones load", async () => {
    let release: () => void = () => {};
    const gate = new Promise<void>((resolve) => {
      release = resolve;
    });
    server.use(
      graphqlResolver("SessionSummary", async ({ variables }) => {
        if (variables.sponsor === "B") await gate;
        return HttpResponse.json({
          data: { sessionSummary: sessionSummary({ mentions: variables.sponsor === "A" ? 47 : 3 }) },
        });
      }),
    );
    const { rerender } = renderWithApollo(<Probe sponsor="A" />);
    expect(screen.getByTestId("loading")).toHaveTextContent("true");
    await waitFor(() => expect(screen.getByTestId("mentions")).toHaveTextContent("47"));
    expect(screen.getByTestId("loading")).toHaveTextContent("false");

    // The sponsor changes: A's numbers leave at once rather than sitting under B's label.
    rerender(<Probe sponsor="B" />);
    expect(screen.getByTestId("mentions")).toHaveTextContent("none");
    expect(screen.getByTestId("loading")).toHaveTextContent("true");

    release();
    await waitFor(() => expect(screen.getByTestId("mentions")).toHaveTextContent("3"));
    expect(screen.getByTestId("loading")).toHaveTextContent("false");
  });
});
