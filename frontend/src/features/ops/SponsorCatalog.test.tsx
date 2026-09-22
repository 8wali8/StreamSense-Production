import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { renderWithApollo } from "../../test/apollo";
import { HttpResponse, restJson, restResolver, server } from "../../test/msw";
import { SponsorCatalog } from "./SponsorCatalog";

const redBull = {
  name: "Red Bull",
  aliases: ["red bull", "redbull"],
  semanticTerms: ["energy drink", "wings"],
  minScore: null,
  updatedAt: 1,
};

describe("SponsorCatalog", () => {
  it("lists the catalog, queues the sponsors deals name that it does not reach, and adds one from the queue", async () => {
    let saved: Record<string, unknown> | null = null;
    server.use(
      restJson("get", "/api/sentiment/relevance/catalog", [redBull]),
      restJson("get", "/api/analytics/deals/sponsors", [
        { sponsor: "redbull", deals: 2, channels: 1, latestStartsAt: 3 },
        { sponsor: "Prime", deals: 1, channels: 1, latestStartsAt: 2 },
      ]),
      restResolver("put", "/api/sentiment/relevance/catalog", async ({ request }) => {
        saved = (await request.json()) as Record<string, unknown>;
        server.use(
          restJson("get", "/api/sentiment/relevance/catalog", [
            redBull,
            { name: "Prime", aliases: ["prime hydration"], semanticTerms: ["hydration"], minScore: null, updatedAt: 2 },
          ]),
        );
        return HttpResponse.json({ ...saved, minScore: null, updatedAt: 2 });
      }),
    );
    const user = userEvent.setup();
    renderWithApollo(<SponsorCatalog />);

    // "redbull" reaches Red Bull through an alias, so its deals count on the entry and it is not queued.
    const entries = within(await screen.findByLabelText("Catalog entries"));
    expect(entries.getByText("Red Bull")).toBeInTheDocument();
    expect(entries.getByText(/also red bull, redbull · 2 terms · 2 deals/)).toBeInTheDocument();
    const queue = within(screen.getByLabelText("Sponsors not in the catalog"));
    expect(queue.getByText("Prime")).toBeInTheDocument();
    expect(queue.queryByText("redbull")).not.toBeInTheDocument();

    // Add starts the form with the name; the terms are typed and the whole entry goes up.
    await user.click(queue.getByRole("button", { name: "Add" }));
    const form = within(screen.getByLabelText("New catalog entry"));
    expect(form.getByLabelText("Sponsor")).toHaveValue("Prime");
    await user.type(form.getByLabelText(/Aliases/), "prime hydration, ");
    await user.type(form.getByLabelText(/Semantic terms/), "hydration");
    await user.click(form.getByRole("button", { name: "Add entry" }));

    expect(await screen.findByText(/Prime saved: 1 aliases, 1 terms/)).toBeInTheDocument();
    expect(saved).toEqual({ name: "Prime", aliases: ["prime hydration"], semanticTerms: ["hydration"] });
    expect(screen.queryByLabelText("Sponsors not in the catalog")).not.toBeInTheDocument();
    expect(within(screen.getByLabelText("Catalog entries")).getByText("Prime")).toBeInTheDocument();
  });

  it("edits an entry in place and removes one", async () => {
    let saved: Record<string, unknown> | null = null;
    let removed: string | null = null;
    server.use(
      restJson("get", "/api/sentiment/relevance/catalog", [redBull]),
      restJson("get", "/api/analytics/deals/sponsors", []),
      restResolver("put", "/api/sentiment/relevance/catalog", async ({ request }) => {
        saved = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json({ ...saved, minScore: 0.6, updatedAt: 2 });
      }),
      restResolver("delete", "/api/sentiment/relevance/catalog/:name", ({ params }) => {
        removed = String(params.name);
        server.use(restJson("get", "/api/sentiment/relevance/catalog", []));
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const user = userEvent.setup();
    renderWithApollo(<SponsorCatalog />);

    const entries = within(await screen.findByLabelText("Catalog entries"));
    await user.click(entries.getByRole("button", { name: "Edit" }));
    const form = within(screen.getByLabelText("Edit Red Bull"));
    expect(form.getByLabelText(/Aliases/)).toHaveValue("red bull, redbull");
    await user.type(form.getByLabelText(/Aliases/), ", rb");
    await user.type(form.getByLabelText(/Minimum relevance score/), "0.6");
    await user.click(form.getByRole("button", { name: "Save entry" }));
    expect(await screen.findByText(/Red Bull saved/)).toBeInTheDocument();
    expect(saved).toEqual({
      name: "Red Bull",
      aliases: ["red bull", "redbull", "rb"],
      semanticTerms: ["energy drink", "wings"],
      minScore: 0.6,
    });

    await user.click(within(screen.getByLabelText("Catalog entries")).getByRole("button", { name: "Remove" }));
    expect(await screen.findByText(/Red Bull removed from the catalog/)).toBeInTheDocument();
    expect(removed).toBe("Red Bull");
    expect(screen.getByText("The catalog is empty.")).toBeInTheDocument();
  });
});
