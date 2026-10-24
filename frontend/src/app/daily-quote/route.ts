import { NextResponse } from "next/server";

const REQUEST_TIMEOUT_MS = 3000;
const SELF_DEVELOPMENT_WORDS = /\b(achiev|ambition|believ|challenge|confidence|courage|discipline|dream|effort|fail|future|goal|greatness|grow|hope|improv|inspir|learn|optimis|persever|persist|potential|practice|progress|purpose|resilien|skill|success|talent|try|wisdom)\w*\b|first step|hard work|think (?:that )?you can/i;
const EXCLUDED_TOPICS = /\b(cook|death|die|dinner|drink|eat|food|god|heart|humor|joke|kill|kiss|love|marriage|politic|pray|religion|romance|stomach|war)\w*\b/i;

type QuoteResponse = { quote?: unknown; author?: unknown };
type PersonResponse = { thumbnail?: { source?: unknown } };
type TranslationResponse = { responseData?: { translatedText?: unknown } };

function shortText(value: unknown, maximum: number) {
  return typeof value === "string" && value.trim().length > 0 && value.trim().length <= maximum ? value.trim() : null;
}

export function isSelfDevelopmentQuote(quote: string) {
  return SELF_DEVELOPMENT_WORDS.test(quote) && !EXCLUDED_TOPICS.test(quote);
}

export async function GET() {
  try {
    const quoteResponse = await fetch("https://dummyjson.com/quotes/random/10", {
      cache: "no-store",
      signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
    });
    if (!quoteResponse.ok) throw new Error("Quote provider unavailable");
    const quotePayload = await quoteResponse.json() as QuoteResponse[];
    if (!Array.isArray(quotePayload)) throw new Error("Invalid quote response");
    const candidates = quotePayload.map(item => ({quote:shortText(item.quote,220),person:shortText(item.author,80)})).filter((item):item is {quote:string;person:string}=>Boolean(item.quote&&item.person&&isSelfDevelopmentQuote(item.quote)));
    const portraits = await Promise.all(candidates.map(async item => {
      try {
        const response=await fetch(`https://en.wikipedia.org/api/rest_v1/page/summary/${encodeURIComponent(item.person)}`, {
          cache:"no-store",
          headers:{"User-Agent":"DailyLeap/1.0 (local learning dashboard)"},
          signal:AbortSignal.timeout(REQUEST_TIMEOUT_MS),
        });
        if(!response.ok)return null;
        const payload=await response.json() as PersonResponse;
        const image=shortText(payload.thumbnail?.source,500);
        return image&&(image.startsWith("https://upload.wikimedia.org/")||image.startsWith("https://thumb.wikimedia.org/"))?{...item,image}:null;
      } catch{return null;}
    }));
    const selected=portraits.find(item=>item!==null);
    if(!selected)throw new Error("Portrait unavailable");

    const translationUrl=new URL("https://api.mymemory.translated.net/get");
    translationUrl.searchParams.set("q",selected.quote);
    translationUrl.searchParams.set("langpair","en|ko");
    const translationResponse=await fetch(translationUrl,{
      cache:"no-store",
      signal:AbortSignal.timeout(REQUEST_TIMEOUT_MS),
    });
    if(!translationResponse.ok)throw new Error("Translation provider unavailable");
    const translationPayload=await translationResponse.json() as TranslationResponse;
    const translatedQuote=shortText(translationPayload.responseData?.translatedText,300);
    if(!translatedQuote||!/[가-힣]/.test(translatedQuote))throw new Error("Korean translation unavailable");

    return NextResponse.json({ ...selected, quote:translatedQuote, source: "online" }, {
      headers: { "Cache-Control": "no-store" },
    });
  } catch {
    return NextResponse.json({ message: "Online quote unavailable" }, { status: 503 });
  }
}
