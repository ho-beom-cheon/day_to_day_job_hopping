import { describe, expect, it } from "vitest";
import { currentMonthlyHero, monthlyHeroForMonth } from "./monthly-hero";

describe("monthly hero", () => {
  it.each(Array.from({ length: 12 }, (_, index) => index + 1))(
    "maps month %i to its own banner image",
    (month) => {
      expect(monthlyHeroForMonth(month)).toBe(
        `/assets/dashboard/monthly/hobeom-hero-${String(month).padStart(2, "0")}-v3.png`,
      );
    },
  );

  it("uses the month from the supplied date", () => {
    expect(currentMonthlyHero(new Date(2026, 8, 21))).toBe(
      "/assets/dashboard/monthly/hobeom-hero-09-v3.png",
    );
  });

  it("falls back to January for an invalid month", () => {
    expect(monthlyHeroForMonth(0)).toBe("/assets/dashboard/monthly/hobeom-hero-01-v3.png");
    expect(monthlyHeroForMonth(13)).toBe("/assets/dashboard/monthly/hobeom-hero-01-v3.png");
  });
});
