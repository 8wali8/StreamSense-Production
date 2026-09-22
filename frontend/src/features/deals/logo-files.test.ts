import { describe, expect, it } from "vitest";
import { logosLead, pickLogoFiles } from "./logo-files";

function file(name: string, type: string): File {
  return new File(["x"], name, { type });
}

describe("pickLogoFiles", () => {
  it("keeps only PNGs and JPEGs, up to the room left on the deal", () => {
    const picked = pickLogoFiles(
      [
        file("a.svg", "image/svg+xml"),
        file("b.png", "image/png"),
        file("c.jpg", "image/jpeg"),
        file("d.png", "image/png"),
      ],
      2,
    );
    expect(picked.map((f) => f.name)).toEqual(["b.png", "c.jpg"]);
  });

  it("picks nothing from nothing, and nothing when the deal is full", () => {
    expect(pickLogoFiles(null, 2)).toEqual([]);
    expect(pickLogoFiles([file("b.png", "image/png")], 0)).toEqual([]);
  });
});

describe("logosLead", () => {
  it("says tracking is off without a logo, and who can fix it", () => {
    expect(logosLead("Red Bull", 0, false)).toBe("On-screen tracking is off until you add the sponsor's logo.");
    expect(logosLead("Red Bull", 0, true)).toBe("On-screen tracking is off for this deal: no logo has been added.");
  });

  it("names the sponsor on a shared tab and explains the change rule to the owner", () => {
    expect(logosLead("Red Bull", 1, true)).toBe("Tracking the Red Bull logo on screen.");
    expect(logosLead("Red Bull", 2, false)).toMatch(/earlier reports keep their numbers/);
  });
});
