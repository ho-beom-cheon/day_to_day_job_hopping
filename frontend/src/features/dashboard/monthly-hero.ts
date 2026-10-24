const monthlyHeroImages = Array.from(
  { length: 12 },
  (_, index) => `/assets/dashboard/monthly/hobeom-hero-${String(index + 1).padStart(2, "0")}-v3.png`,
);

export function monthlyHeroForMonth(month: number) {
  const safeMonth = Number.isInteger(month) && month >= 1 && month <= 12 ? month : 1;
  return monthlyHeroImages[safeMonth - 1];
}

export function currentMonthlyHero(now = new Date()) {
  return monthlyHeroForMonth(now.getMonth() + 1);
}
