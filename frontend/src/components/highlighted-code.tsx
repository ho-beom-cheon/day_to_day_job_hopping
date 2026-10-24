import type { ReactNode } from "react";
import styles from "./highlighted-code.module.css";

const keywords: Record<string, Set<string>> = {
  java: new Set("abstract assert boolean break byte case catch char class const continue default do double else enum extends final finally float for if implements import instanceof int interface long native new package private protected public record return sealed short static strictfp super switch synchronized this throw throws transient try var void volatile while yield".split(" ")),
  javascript: new Set("async await break case catch class const continue default delete do else export extends false finally for from function if import in instanceof let new null of return static super switch this throw true try typeof var void while yield".split(" ")),
  typescript: new Set("as async await boolean break case catch class const continue default do else enum export extends false finally for from function if implements import in instanceof interface let new null number of private protected public readonly return static string super switch this throw true try type typeof undefined var void while yield".split(" ")),
  python: new Set("and as assert async await break class continue def del elif else except False finally for from global if import in is lambda None nonlocal not or pass raise return self True try while with yield".split(" ")),
  sql: new Set("add all alter and as asc between by case cast coalesce constraint count create cross delete desc distinct drop else end exists false from full group having in inner insert into is join left like limit not null offset on or order outer primary right select set table then true union unique update values when where with".split(" ")),
};

const languageAliases: Record<string, string> = {
  js: "javascript", ts: "typescript", py: "python", shell: "bash", sh: "bash",
};

type TokenKind = "keyword" | "string" | "comment" | "number" | "type" | "function" | "annotation" | "property";

function tokenKind(token: string, language: string, source: string, nextIndex: number): TokenKind | undefined {
  if (/^(?:\/\/|\/\*|#|--)/.test(token)) return "comment";
  if (/^["'`]/.test(token)) return "string";
  if (/^\d/.test(token)) return "number";
  if (token.startsWith("@")) return "annotation";
  if (language === "json" && /^\s*:/.test(source.slice(nextIndex))) return "property";

  const word = language === "sql" ? token.toLowerCase() : token;
  if (keywords[language]?.has(word) || ["true", "false", "null"].includes(word)) return "keyword";
  if (/^[A-Z]/.test(token) && ["java", "javascript", "typescript", "python"].includes(language)) return "type";
  if (/^\s*\(/.test(source.slice(nextIndex)) && language !== "json") return "function";
  return undefined;
}

function highlightedTokens(source: string, language: string): ReactNode[] {
  const comment = language === "python" || language === "bash" ? "#[^\\n]*" : language === "sql" ? "--[^\\n]*" : "\\/\\/[^\\n]*|\\/\\*[\\s\\S]*?\\*\\/";
  const pattern = new RegExp(`${comment}|"(?:\\\\.|[^"\\\\])*"|'(?:\\\\.|[^'\\\\])*'|\`(?:\\\\.|[^\`\\\\])*\`|@[A-Za-z_$][\\w$]*|\\b\\d+(?:\\.\\d+)?\\b|[A-Za-z_$][\\w$]*`, "g");
  const parts: ReactNode[] = [];
  let offset = 0;

  for (const match of source.matchAll(pattern)) {
    const index = match.index;
    if (index > offset) parts.push(source.slice(offset, index));
    const token = match[0];
    const kind = tokenKind(token, language, source, index + token.length);
    parts.push(kind ? <span className={styles[kind]} key={index}>{token}</span> : token);
    offset = index + token.length;
  }
  if (offset < source.length) parts.push(source.slice(offset));
  return parts;
}

/** Syntax color only: the original code, whitespace and newlines stay untouched. */
export function HighlightedCode({ children, className }: { children: ReactNode; className?: string }) {
  const taggedLanguage = className?.match(/language-([\w+-]+)/)?.[1]?.toLowerCase();
  const language = taggedLanguage ? languageAliases[taggedLanguage] ?? taggedLanguage : undefined;
  const source = typeof children === "string" ? children : undefined;
  const highlight = source !== undefined && language && (keywords[language] || language === "json" || language === "bash");

  return <code className={`${styles.code}${className ? ` ${className}` : ""}`}>
    {highlight ? highlightedTokens(source, language) : children}
  </code>;
}
