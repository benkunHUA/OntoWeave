import { MessageSquareText, Sparkles } from 'lucide-react'
import { ChatScrollArea } from '../../../shared/chat/components/ChatScrollArea'
import { Composer } from '../../../shared/chat/components/Composer'
import { ErrorToast } from '../../../shared/chat/components/ErrorToast'
import type { PendingInterrupt } from '../../../shared/chat/lib/messageBlocks'
import type { ChatMessage, InterruptOption } from '../../../shared/types'
import { QUERY_COPY } from '../lib/constants'
import { QueryMessageBubble } from './QueryMessageBubble'

interface QueryChatPaneProps {
  title: string
  messages: ChatMessage[]
  isRunning: boolean
  isLoading: boolean
  hasConversation: boolean
  interrupt: PendingInterrupt | null
  error: string | null
  onClearError: () => void
  onSend: (text: string) => void
  onAnswer: (option: InterruptOption) => void
  onAbort: () => void
}

/** 右侧问答区：标题、消息、证据、输入与错误提示。 */
export function QueryChatPane({
  title,
  messages,
  isRunning,
  isLoading,
  hasConversation,
  interrupt,
  error,
  onClearError,
  onSend,
  onAnswer,
  onAbort,
}: QueryChatPaneProps) {
  const pendingInterruptId = interrupt?.blockId ?? null
  const scrollKey = messages[0]?.id ?? 'empty'

  return (
    <section className="chat-pane query-chat">
      <header className="chat-header query-chat__header">
        <div>
          <span className="eyebrow">Business query</span>
          <h1>{title || QUERY_COPY.brandTitle}</h1>
        </div>
        <span className={`chat-header__status ${isRunning ? 'is-running' : ''}`}>
          <span />
          {isRunning ? '查询中' : hasConversation ? '就绪' : '未选择'}
        </span>
      </header>

      <ChatScrollArea className="query-chat__scroll" resetKey={scrollKey}>
        {!hasConversation ? (
          <div className="chat-empty">
            <MessageSquareText size={26} />
            <strong>{QUERY_COPY.selectEmptyTitle}</strong>
            <p>{QUERY_COPY.selectEmptyBody}</p>
          </div>
        ) : null}
        {hasConversation && !messages.length && !isLoading ? (
          <div className="chat-empty">
            <Sparkles size={26} />
            <strong>{QUERY_COPY.chatEmptyTitle}</strong>
            <p>{QUERY_COPY.chatEmptyBody}</p>
          </div>
        ) : null}
        {isLoading ? (
          <div className="chat-empty chat-empty--loading">
            <MessageSquareText size={22} />
            <span>{QUERY_COPY.loading}</span>
          </div>
        ) : null}
        {messages.map((message) => (
          <QueryMessageBubble
            key={message.id}
            message={message}
            pendingInterruptId={pendingInterruptId}
            isRunning={isRunning}
            onAnswer={onAnswer}
          />
        ))}
      </ChatScrollArea>

      <Composer
        disabled={!hasConversation || isLoading}
        isRunning={isRunning}
        interrupt={interrupt}
        placeholder={QUERY_COPY.composerPlaceholder}
        onSend={onSend}
        onAbort={onAbort}
      />
      <ErrorToast message={error} onClose={onClearError} />
    </section>
  )
}
