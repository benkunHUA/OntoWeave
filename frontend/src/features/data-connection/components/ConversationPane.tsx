import { Loader } from '@mantine/core'
import { CircleCheck, Sparkles } from 'lucide-react'
import { useMemo } from 'react'
import { ChatScrollArea } from '../../../shared/chat/components/ChatScrollArea'
import { ChatPaneHeader } from '../../../shared/chat/components/ChatPaneHeader'
import { Composer } from '../../../shared/chat/components/Composer'
import { ErrorToast } from '../../../shared/chat/components/ErrorToast'
import { MessageBubble } from '../../../shared/chat/components/MessageBubble'
import { EmptyState } from '../../../shared/components/EmptyState'
import type { PendingInterrupt } from '../../../shared/chat/lib/messageBlocks'
import type { ChatMessage, ConversationEvent, InterruptOption } from '../../../shared/types'
import { mergeTimeline, type TimelineItem } from '../lib/operationTimeline'
import { EventCard, OperationCard } from './TimelineEventCard'

interface ConversationPaneProps {
  title: string
  messages: ChatMessage[]
  events: ConversationEvent[]
  isRunning: boolean
  isLoading: boolean
  interrupt: PendingInterrupt | null
  error: string | null
  onClearError: () => void
  onSend: (text: string) => void
  onAnswer: (option: InterruptOption) => void
  onAbort: () => void
  onExplain: (summary: string) => void
  onOpenDataset: (datasetId: string) => void
}

function timelineItemKey(item: TimelineItem): string {
  if (item.kind === 'message') return item.message.id
  if (item.kind === 'event') return item.event.id
  return item.operationId
}

export function ConversationPane({
  title,
  messages,
  events,
  isRunning,
  isLoading,
  interrupt,
  error,
  onClearError,
  onSend,
  onAnswer,
  onAbort,
  onExplain,
  onOpenDataset,
}: ConversationPaneProps) {
  const timeline = useMemo(() => mergeTimeline(messages, events), [messages, events])
  const pendingInterruptId = interrupt?.blockId ?? null
  const firstItem = timeline[0]
  const scrollKey = firstItem ? timelineItemKey(firstItem) : 'empty'

  return (
    <section className="chat-pane data-connection-chat">
      <ChatPaneHeader
        eyebrow="Data connection"
        title={title || '数据连接助手'}
        status={isRunning ? '处理中' : '在线'}
        statusColor={isRunning ? 'blue' : 'teal'}
        statusIcon={isRunning ? <Loader size={12} /> : <CircleCheck size={12} />}
      />

      <ChatScrollArea resetKey={scrollKey}>
        {!timeline.length && !isLoading ? (
          <EmptyState
            className="chat-empty"
            icon={<Sparkles size={22} />}
            title="从一个数据源开始"
            body="在右侧填写连接信息；保存、测试、选表和同步都会回到这里。"
          />
        ) : null}

        {timeline.map((item) => {
          if (item.kind === 'message') {
            return (
              <MessageBubble
                key={`message-${item.message.id}`}
                message={item.message}
                pendingInterruptId={pendingInterruptId}
                isRunning={isRunning}
                onAnswer={onAnswer}
              />
            )
          }
          if (item.kind === 'operation') {
            return (
              <OperationCard
                key={`operation-${item.operationId}`}
                title={item.title}
                status={item.status}
                events={item.events}
                onExplain={onExplain}
                onOpenDataset={onOpenDataset}
              />
            )
          }
          return (
            <EventCard
              key={`event-${item.event.id}`}
              event={item.event}
              onOpenDataset={onOpenDataset}
            />
          )
        })}

        {isLoading ? (
          <EmptyState
            className="chat-empty chat-empty--loading"
            icon={<Loader size="sm" />}
            title="正在读取会话记录…"
          />
        ) : null}
      </ChatScrollArea>

      <Composer
        isRunning={isRunning}
        interrupt={interrupt}
        placeholder="描述你想连接的数据、同步方式或遇到的问题"
        onSend={onSend}
        onAbort={onAbort}
      />
      <ErrorToast message={error} onClose={onClearError} />
    </section>
  )
}
