import { describe, expect, it } from "vitest";
import { dealDates, feeMultiple, fromDateInput, streamsProgress, toDateInput } from "./deal-format";

describe("deal-format", () => {
  it("prices media value against the fee only when both exist", () => {
    expect(feeMultiple(3500, 2500)).toBe(1.4);
    expect(feeMultiple(3500, 0)).toBeNull();
    expect(feeMultiple(null, 2500)).toBeNull();
    expect(feeMultiple(3500, null)).toBeNull();
  });

  it("describes progress against promised streams", () => {
    expect(streamsProgress(3, 4)).toBe("3 of 4 streams");
    expect(streamsProgress(1, null)).toBe("1 stream");
    expect(streamsProgress(2, null)).toBe("2 streams");
  });

  it("round-trips a date input at local midnight, with the end of the day exclusive", () => {
    const start = fromDateInput("2026-09-01");
    expect(start).not.toBeNull();
    expect(toDateInput(start as number)).toBe("2026-09-01");
    expect((fromDateInput("2026-09-01", true) as number) - (start as number)).toBe(24 * 3_600_000);
    expect(fromDateInput("nope")).toBeNull();
  });

  it("shows the last day inside the deal, not the exclusive bound the form stores", () => {
    const start = fromDateInput("2026-09-01") as number;
    const end = fromDateInput("2026-09-30", true) as number;
    expect(dealDates(start, end)).toMatch(/Sep 1 – Sep 30, 2026/);
    expect(dealDates(start, null)).toMatch(/since Sep 1, 2026/);
  });
});
