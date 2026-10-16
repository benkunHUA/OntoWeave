import type { ChatMessage, InterruptOption } from '../../../shared/types'
import { isNoticeMessage } from '../lib/messageBlocks'
import { ChatNotice } from './ChatNotice'
import { InterruptCard } from './InterruptCard'
import { MarkdownText } from './MarkdownText'
import { ThinkingBlock } from './ThinkingBlock'
import { ToolCallBlock } from './ToolCallBlock'

interface MessageBubbleProps {
  message: ChatMessage
  /** 当前等待回答的中断块 id，只有它上的选项可点。 */
  pendingInterruptId: string | null
  isRunning: boolean
  onAnswer: (option: InterruptOption) => void
}

/** 一条消息气泡：按块类型挑渲染器；助手正文走 Markdown，用户正文按纯文本。 */
export function MessageBubble({
  message,
  pendingInterruptId,
  isRunning,
  onAnswer,
}: MessageBubbleProps) {
  return (
    <article
      className={`message-bubble message-bubble--${message.role} ${
        message.kind === 'error' ? 'is-error' : ''
      } ${isNoticeMessage(message.blocks) ? 'is-notice' : ''}`}
    >
      {message.blocks.length === 0 ? <span className="typing-dot" /> : null}
      {message.blocks.map((block) => {
        if (block.kind === 'notice') {
          return <ChatNotice key={block.id} block={block} />
        }
        if (block.kind === 'reasoning') {
          return <ThinkingBlock key={block.id} text={block.text} done={Boolean(block.done)} />
        }
        if (block.kind === 'tool') {
          return <ToolCallBlock key={block.id} block={block} />
        }
        if (block.kind === 'interrupt') {
          return (
            <InterruptCard
              key={block.id}
              block={block}
              pending={pendingInterruptId === block.id}
              disabled={isRunning}
              onAnswer={onAnswer}
            />
          )
        }
        if (message.role === 'user') {
          return (
            <p key={block.id} className="message-text">
              {block.text}
            </p>
          )
        }
        return <MarkdownText key={block.id} text={block.text} />
      })}
    </article>
  )
}
