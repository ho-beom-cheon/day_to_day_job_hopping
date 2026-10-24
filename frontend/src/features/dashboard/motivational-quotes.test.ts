import { describe, expect, it } from "vitest";
import { savedDeveloperQuotes } from "./motivational-quotes";

describe("savedDeveloperQuotes", () => {
  it("keeps twenty offline fallback quotes with matching local portraits", () => {
    expect(savedDeveloperQuotes).toHaveLength(20);
    expect(savedDeveloperQuotes.every(item => item.source === "local")).toBe(true);
    expect(savedDeveloperQuotes.every(item => item.image.startsWith("/assets/quotes/"))).toBe(true);
  });
});

