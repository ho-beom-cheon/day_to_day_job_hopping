import { afterEach, describe, expect, it, vi } from "vitest";
import { GET, isSelfDevelopmentQuote } from "./route";

afterEach(() => vi.unstubAllGlobals());

describe("GET /daily-quote", () => {
  it("accepts growth themes and rejects unrelated topics", () => {
    expect(isSelfDevelopmentQuote("Success comes from persistent effort and learning from failure.")).toBe(true);
    expect(isSelfDevelopmentQuote("Your light taught my heart how to love.")).toBe(false);
    expect(isSelfDevelopmentQuote("The secret to humor is surprise.")).toBe(false);
    expect(isSelfDevelopmentQuote("Indigestion enforces morality on the stomach.")).toBe(false);
    expect(isSelfDevelopmentQuote("Man is descended from a hairy quadruped, arboreal in its habits.")).toBe(false);
  });

  it("returns an online quote only when its Wikimedia portrait is available", async () => {
    const fetchMock=vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify([{quote:"Stay curious and keep learning.",author:"Ada Lovelace"}]),{status:200}))
      .mockResolvedValueOnce(new Response(JSON.stringify({thumbnail:{source:"https://upload.wikimedia.org/ada.jpg"}}),{status:200}))
      .mockResolvedValueOnce(new Response(JSON.stringify({responseData:{translatedText:"호기심을 잃지 말고 계속 배우세요."}}),{status:200}));
    vi.stubGlobal("fetch",fetchMock);

    const response=await GET();

    expect(response.status).toBe(200);
    await expect(response.json()).resolves.toEqual({quote:"호기심을 잃지 말고 계속 배우세요.",person:"Ada Lovelace",image:"https://upload.wikimedia.org/ada.jpg",source:"online"});
  });

  it("falls back instead of exposing English when Korean translation fails", async () => {
    const fetchMock=vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify([{quote:"Stay curious and keep learning.",author:"Ada Lovelace"}]),{status:200}))
      .mockResolvedValueOnce(new Response(JSON.stringify({thumbnail:{source:"https://upload.wikimedia.org/ada.jpg"}}),{status:200}))
      .mockResolvedValueOnce(new Response(JSON.stringify({responseData:{translatedText:"Stay curious and keep learning."}}),{status:200}));
    vi.stubGlobal("fetch",fetchMock);

    const response=await GET();

    expect(response.status).toBe(503);
  });

  it("returns 503 so the client can use its saved fallback", async () => {
    vi.stubGlobal("fetch",vi.fn().mockRejectedValue(new Error("offline")));
    const response=await GET();
    expect(response.status).toBe(503);
  });

  it("uses the saved fallback when every external quote is off-topic", async () => {
    const fetchMock=vi.fn().mockResolvedValueOnce(new Response(JSON.stringify([
      {quote:"Your light taught my heart how to love.",author:"Rumi"},
      {quote:"The secret to humor is surprise.",author:"Aristotle"},
    ]),{status:200}));
    vi.stubGlobal("fetch",fetchMock);

    const response=await GET();

    expect(response.status).toBe(503);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
