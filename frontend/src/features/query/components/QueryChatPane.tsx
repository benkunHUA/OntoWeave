import { Loader } from '@mantine/core'
import { CircleCheck, MessageSquareText, Sparkles } from 'lucide-react'
import { ChatPaneHeader } from '../../../shared/chat/components/ChatPaneHeader'
import { ChatScrollArea } from '../../../shared/chat/components/ChatScrollArea'
import { Composer } from '../../../shared/chat/components/Composer'
import { ErrorToast } from '../../../shared/chat/components/ErrorToast'
import { EmptyState } from '../../../shared/components/EmptyState'
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
      <ChatPaneHeader
        eyebrow="Business query"
        title={title || QUERY_COPY.brandTitle}
        status={isRunning ? '查询中' : hasConversation ? '就绪' : '未选择'}
        statusColor={isRunning ? 'blue' : hasConversation ? 'teal' : 'gray'}
        statusIcon={isRunning ? <Loader size={12} /> : <CircleCheck size={12} />}
      />

      <ChatScrollArea className="query-chat__scroll" resetKey={scrollKey}>
        {!hasConversation ? (
          <EmptyState
            className="chat-empty"
            icon={<MessageSquareText size={22} />}
            title={QUERY_COPY.selectEmptyTitle}
            body={QUERY_COPY.selectEmptyBody}
          />
        ) : null}
        {hasConversation && !messages.length && !isLoading ? (
          <EmptyState
            className="chat-empty"
            icon={<Sparkles size={22} />}
            title={QUERY_COPY.chatEmptyTitle}
            body={QUERY_COPY.chatEmptyBody}
          />
        ) : null}
        {isLoading ? (
          <EmptyState
            className="chat-empty chat-empty--loading"
            icon={<Loader size="sm" />}
            title={QUERY_COPY.loading}
          />
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
