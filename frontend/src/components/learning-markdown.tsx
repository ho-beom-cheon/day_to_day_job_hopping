import Markdown from "react-markdown";
import { formatLessonCode } from "./format-lesson-code";
import { HighlightedCode } from "./highlighted-code";

/** Course text supports CommonMark; raw HTML and remote images are not rendered. */
export function LearningMarkdown({ body }: { body: string }) {
  return <div className="min-w-0 max-w-full break-words text-sm leading-7 text-slate-700 [&>p]:my-4 [&_li]:my-1 [&_ol]:my-4 [&_ol]:list-decimal [&_ol]:pl-6 [&_ul]:my-4 [&_ul]:list-disc [&_ul]:pl-6 [&_blockquote]:border-l-4 [&_blockquote]:border-blue-300 [&_blockquote]:pl-4 [&_code]:font-mono [&_code]:text-[0.9em]">
    <Markdown skipHtml disallowedElements={["img"]} components={{
      h1: ({ children }) => <h3 className="mb-4 mt-8 text-xl font-extrabold text-slate-950">{children}</h3>,
      h2: ({ children }) => <h3 className="mb-3 mt-7 text-lg font-extrabold text-slate-950">{children}</h3>,
      h3: ({ children }) => <h4 className="mb-2 mt-6 font-extrabold text-slate-950">{children}</h4>,
      code: ({ children, className }) => <HighlightedCode className={className}>
        {className === "language-java" && typeof children === "string" ? formatLessonCode(children) : children}
      </HighlightedCode>,
      pre: ({ children }) => <pre tabIndex={0} aria-label="코드 예제" className="my-5 max-w-full overflow-x-auto rounded-2xl border border-slate-200 bg-slate-50 p-4 leading-6 focus-visible:outline-2 focus-visible:outline-blue-600">{children}</pre>,
      a: ({ href, children }) => href ? <a href={href} target="_blank" rel="noreferrer noopener" className="break-all font-semibold text-blue-700 underline underline-offset-4">{children}</a> : <span>{children}</span>,
    }}>{body}</Markdown>
  </div>;
}
