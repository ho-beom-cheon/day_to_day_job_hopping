import { Children, isValidElement } from "react";
import Markdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { HighlightedCode } from "./highlighted-code";
import styles from "./question-content.module.css";

const blockLabels: Record<string, string> = {
  text: "예시", plaintext: "예시", example: "예시", input: "입력 예시", output: "출력 예시",
  java: "Java", javascript: "JavaScript", js: "JavaScript", typescript: "TypeScript",
  ts: "TypeScript", sql: "SQL", json: "JSON", python: "Python", bash: "Shell", shell: "Shell",
};

/** A plain, bounded preview for linked lists; formatting stays on the detail screen. */
export function QuestionSummary({ body }: { body: string }) {
  return <span className="line-clamp-2 break-words font-normal leading-7">
    <Markdown skipHtml allowedElements={["p", "code"]} unwrapDisallowed components={{
      p: ({ children }) => <span>{children}{" "}</span>,
      code: ({ children }) => <span>{children}</span>,
    }}>{body}</Markdown>
  </span>;
}

/** Shared, non-interactive Markdown (with GFM tables) for task material, questions and feedback. */
export function QuestionContent({ body, compact = false }: { body: string; compact?: boolean }) {
  return <div className={`${styles.content}${compact ? ` ${styles.compact}` : ""}`}>
    <Markdown skipHtml remarkPlugins={[remarkGfm]} disallowedElements={["img"]} components={{
      table: ({ children }) => <div className={styles.tableScroll} tabIndex={0}><table>{children}</table></div>,
      // Question content must never create another page title or an interactive link inside a choice.
      h1: ({ children }) => <h3>{children}</h3>,
      h2: ({ children }) => <h3>{children}</h3>,
      h3: ({ children }) => <h3>{children}</h3>,
      h4: ({ children }) => <h3>{children}</h3>,
      h5: ({ children }) => <h3>{children}</h3>,
      h6: ({ children }) => <h3>{children}</h3>,
      a: ({ children }) => <span>{children}</span>,
      code: ({ children, className }) => <HighlightedCode className={className}>{children}</HighlightedCode>,
      blockquote: ({ children }) => <aside className={styles.example} aria-label="보기">
        <div className={styles.caption}>보기</div><div className={styles.exampleBody}>{children}</div>
      </aside>,
      pre: ({ children }) => {
        const child = Children.toArray(children)[0];
        const language = isValidElement<{ className?: string }>(child)
          ? /language-([\w+-]+)/.exec(child.props.className ?? "")?.[1]?.toLowerCase() : undefined;
        const isExample = language && ["text", "plaintext", "example", "input", "output"].includes(language);
        const label = isExample ? blockLabels[language] : `코드${language ? ` · ${blockLabels[language] ?? language.slice(0, 32)}` : ""}`;
        return <figure className={styles.codeBlock}>
          <figcaption className={styles.caption}>{label}</figcaption>
          <pre tabIndex={0} aria-label={label}>{children}</pre>
        </figure>;
      },
    }}>{body}</Markdown>
  </div>;
}
