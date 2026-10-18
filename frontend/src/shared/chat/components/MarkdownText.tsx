import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'

/** 助手正文的 markdown 渲染。不启用裸 HTML，默认就不会被注入。 */
export function MarkdownText({ text }: { text: string }) {
  return (
    <div className="markdown-body">
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          a: ({ node, ...props }) => {
            void node
            return <a {...props} target="_blank" rel="noreferrer noopener" />
          },
        }}
      >
        {text}
      </ReactMarkdown>
    </div>
  )
}
