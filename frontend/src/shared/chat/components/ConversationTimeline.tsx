import { useMemo, type ReactNode } from 'react'
import type { ChatMessage, ConversationEvent, InterruptOption } from '../../types'
import { EmptyState } from '../../components/EmptyState'
import type { PendingInterrupt } from '../lib/messageBlocks'
import { ChatScrollArea } from './ChatScrollArea'
import { MessageBubble } from './MessageBubble'

interface ConversationTimelineProps {
  messages: ChatMessage[]
  events: ConversationEvent[]
  isRunning: boolean
  isLoading: boolean
  interrupt: PendingInterrupt | null
  onAnswer: (option: InterruptOption) => void
  eventFilter?: (event: ConversationEvent) => boolean
  eventIcon?: ReactNode
  eventClassName?: string
  eventIconClassName?: string
  emptyIcon?: ReactNode
  emptyTitle?: string
  emptyBody?: string
  loadingIcon?: ReactNode
  loadingLabel?: string
}

type TimelineItem =
  | { kind: 'message'; at: string; message: ChatMessage }
  | { kind: 'event'; at: string; event: ConversationEvent }

function timeOf(value: string): number {
  const parsed = Date.parse(value)
  return Number.isNaN(parsed) ? 0 : parsed
}

export function ConversationTimeline({
  messages,
  events,
  isRunning,
  isLoading,
  interrupt,
  onAnswer,
  eventFilter,
  eventIcon,
  eventClassName = 'timeline-event-card',
  eventIconClassName = 'timeline-event-card__icon',
  emptyIcon,
  emptyTitle,
  emptyBody,
  loadingIcon,
  loadingLabel,
}: ConversationTimelineProps) {
  const timeline = useMemo<TimelineItem[]>(() => {
    const items: TimelineItem[] = [
      ...messages.map((message) => ({
        kind: 'message' as const,
        at: message.created_at ?? new Date(0).toISOString(),
        message,
      })),
      ...events
        .filter((event) => eventFilter?.(event) ?? true)
        .map((event) => ({
          kind: 'event' as const,
          at: event.created_at,
          event,
        })),
    ]
    return items.sort((left, right) => timeOf(left.at) - timeOf(right.at))
  }, [eventFilter, events, messages])

  const pendingInterruptId = interrupt?.blockId ?? null
  const firstItem = timeline[0]
  const scrollKey = firstItem
    ? firstItem.kind === 'message'
      ? firstItem.message.id
      : firstItem.event.id
    : 'empty'

  return (
    <ChatScrollArea resetKey={scrollKey}>
      {!timeline.length && !isLoading && emptyTitle ? (
        <EmptyState
          className="chat-empty"
          icon={emptyIcon}
          title={emptyTitle}
          body={emptyBody}
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
        return (
          <article key={`event-${item.event.id}`} className={eventClassName}>
            <span className={eventIconClassName}>{eventIcon}</span>
            <div>
              <strong>{item.event.summary}</strong>
              <time>{new Date(item.event.created_at).toLocaleString()}</time>
            </div>
          </article>
        )
      })}

      {isLoading ? (
        <EmptyState
          className="chat-empty chat-empty--loading"
          icon={loadingIcon}
          title={loadingLabel ?? '正在读取会话…'}
        />
      ) : null}
    </ChatScrollArea>
  )
}
