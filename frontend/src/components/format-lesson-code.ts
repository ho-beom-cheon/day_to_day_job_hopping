/** Readability pass for the already published Java lesson examples. */
export function formatLessonCode(source: string): string {
  if (!/^import java\.util\.\*;\r?\n/.test(source) || !/public class Lesson\d{2}\s*\{/.test(source)) {
    return source;
  }

  const lines: string[] = [];
  const blocks: Array<{ inline: boolean; parenDepth: number }> = [];
  let current = "";
  let indent = 0;
  let parenDepth = 0;
  let quote: "'" | '"' | null = null;
  let escaped = false;

  function flush() {
    const content = current.trim();
    if (content) lines.push(`${"    ".repeat(indent)}${content}`);
    current = "";
  }

  for (let index = 0; index < source.length; index++) {
    const character = source[index];

    if (quote) {
      current += character;
      if (escaped) escaped = false;
      else if (character === "\\") escaped = true;
      else if (character === quote) quote = null;
      continue;
    }
    if (character === '"' || character === "'") {
      quote = character;
      current += character;
      continue;
    }
    if (character === "(" ) parenDepth++;
    if (character === ")") parenDepth--;

    if (character === "{" ) {
      const before = current.trimEnd();
      const inline = /(?:=|\[)$/.test(before) && !/->\s*$/.test(before);
      blocks.push({ inline, parenDepth });
      current += character;
      if (!inline) {
        flush();
        indent++;
      }
      continue;
    }
    if (character === "}") {
      const block = blocks.pop();
      if (block && !block.inline) {
        flush();
        indent = Math.max(0, indent - 1);
        current += "}";
        const rest = source.slice(index + 1).match(/^\s*([;)])/);
        if (!rest) flush();
      } else current += character;
      continue;
    }
    if (character === ";") {
      current += character;
      const block = [...blocks].reverse().find((item) => !item.inline);
      if (parenDepth <= (block?.parenDepth ?? 0)) flush();
      continue;
    }
    if (character === "\n") {
      flush();
      continue;
    }
    current += character;
  }
  flush();

  const spaced: string[] = [];
  for (const line of lines) {
    const text = line.trimStart();
    const previous = spaced.at(-1)?.trimStart() ?? "";
    const depth = (line.length - text.length) / 4;
    const setup = /^(?:var |String |int\[\] |int |long |double |List<|Map<|Set<|Future<|record |class |interface )/.test(text);
    const beforeCheck = /^(?:if\s*\(|try\s*[({]|for\s*\()/.test(text);
    const priorSetup = /^(?:var |String |int\[\] |int |long |double |List<|Map<|Set<|Future<)/.test(previous);
    const priorCheck = /^(?:if\s*\(|try\s*[({]|for\s*\()/.test(previous);
    const finalPrint = /^System\.out\.println\("Lesson\d{2} OK"\)/.test(text);
    const afterImports = text.startsWith("public class Lesson") && previous.startsWith("import ");
    const afterBlock = depth === 2 && previous === "}" && !/^(?:catch|else)\b/.test(text);
    const separate = afterImports || finalPrint || afterBlock || (depth === 2 && ((setup && priorCheck) || (beforeCheck && priorSetup)));

    if (separate && spaced.length && spaced.at(-1) !== "") spaced.push("");
    spaced.push(line);
  }
  return `${spaced.join("\n")}\n`;
}
