import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { renderWithApollo } from "../../test/apollo";
import { graphqlData, graphqlError, server } from "../../test/msw";
import { Health } from "./Health";

describe("Health", () => {
  it("renders the gateway health string", async () => {
    server.use(graphqlData("Health", { health: "ok" }));

    renderWithApollo(<Health />);

    expect(screen.getByText("Health: loading...")).toBeInTheDocument();
    expect(await screen.findByText("Health: ok")).toBeInTheDocument();
  });

  it("renders the GraphQL error", async () => {
    server.use(graphqlError("Health", "gateway unavailable"));

    renderWithApollo(<Health />);

    expect(await screen.findByText("Health: error - a downstream service is unavailable")).toBeInTheDocument();
  });
});
