import { readFileSync, readdirSync } from "node:fs";
import { join, resolve } from "node:path";
import { describe, expect, it } from "vitest";
import { formatLessonCode } from "./format-lesson-code";

const examplesDirectory = resolve(process.cwd(), "../content/java-four-week-v1/examples");
const codeTokens = (source: string) => source.match(/"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|[A-Za-z_$][\w$]*|\d+|[^\s]/g);

describe("published lesson code formatting", () => {
  it("separates setup, checks and output in the shown Java example", () => {
    const source = readFileSync(join(examplesDirectory, "Lesson01.java"), "utf8");
    const formatted = formatLessonCode(source);

    expect(formatted).toContain('String b = new String("java");\n\n        if');
    expect(formatted).toContain("int[] x = {1, 2};\n        int[] y = {1, 2};");
    expect(formatted).toContain("\n\n        System.out.println");
    expect(formatted).toContain("    public static void main");
  });

  it("preserves the tokens and literals of all twenty runnable examples", () => {
    const names = readdirSync(examplesDirectory).filter((name) => /^Lesson\d{2}\.java$/.test(name));
    expect(names).toHaveLength(20);

    for (const name of names) {
      const source = readFileSync(join(examplesDirectory, name), "utf8");
      const formatted = formatLessonCode(source);
      expect(codeTokens(formatted), name).toEqual(codeTokens(source));
      expect(formatLessonCode(formatted), name).toBe(formatted);
    }
  });

  it("leaves other Java snippets unchanged", () => {
    const source = 'if (ready) {\n  run();\n}\n';
    expect(formatLessonCode(source)).toBe(source);
  });
});
