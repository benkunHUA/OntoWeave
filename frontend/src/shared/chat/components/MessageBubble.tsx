import type { ChatMessage, InterruptOption } from '../../../shared/types'
import { orderMessageBlocks } from '../lib/executionTrace'
import { isNoticeMessage } from '../lib/messageBlocks'
import { ChatNotice } from './ChatNotice'
import { ExecutionTrace } from './execution/ExecutionTrace'
import { InterruptCard } from './InterruptCard'
import { MarkdownText } from './MarkdownText'

interface MessageBubbleProps {
  message: ChatMessage
  /** 当前等待回答的中断块 id，只有它上的选项可点。 */
  pendingInterruptId: string | null
  isRunning: boolean
  onAnswer: (option: InterruptOption) => void
  executionLabel?: string
  active?: boolean
}

/** 一条消息气泡：执行轨迹折叠在前，正文随后；助手正文走 Markdown，用户正文按纯文本。 */
export function MessageBubble({
  message,
  pendingInterruptId,
  isRunning,
  onAnswer,
  executionLabel,
  active = false,
}: MessageBubbleProps) {
  const groups = orderMessageBlocks(message.blocks)
  const activeTraceId = active ? (groups.traces.at(-1)?.id ?? null) : null

  return (
    <article
      className={`message-bubble message-bubble--${message.role} ${
        message.kind === 'error' ? 'is-error' : ''
      } ${isNoticeMessage(message.blocks) ? 'is-notice' : ''}`}
    >
      {message.blocks.length === 0 ? <span className="typing-dot" /> : null}
      {groups.traces.map((trace) => (
        <ExecutionTrace
          key={trace.id}
          trace={trace}
          label={executionLabel}
          active={trace.id === activeTraceId}
        />
      ))}
      {groups.notices.map((block) => (
        <ChatNotice key={block.id} block={block} />
      ))}
      {groups.text.map((block) =>
        message.role === 'user' ? (
          <p key={block.id} className="message-text">
            {block.text}
          </p>
        ) : (
          <MarkdownText key={block.id} text={block.text} />
        ),
      )}
      {groups.interrupts.map((block) => (
        <InterruptCard
          key={block.id}
          block={block}
          pending={pendingInterruptId === block.id}
          disabled={isRunning}
          onAnswer={onAnswer}
        />
      ))}
    </article>
  )
}
