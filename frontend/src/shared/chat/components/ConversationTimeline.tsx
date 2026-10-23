import { useEffect, useMemo, useRef, type ReactNode } from 'react'
import type { ChatMessage, ConversationEvent, InterruptOption } from '../../types'
import type { PendingInterrupt } from '../lib/messageBlocks'
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
  const endRef = useRef<HTMLDivElement | null>(null)
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

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [timeline.length, pendingInterruptId])

  return (
    <div className="message-scroll">
      {!timeline.length && !isLoading && emptyTitle ? (
        <div className="chat-empty">
          {emptyIcon}
          <strong>{emptyTitle}</strong>
          {emptyBody ? <p>{emptyBody}</p> : null}
        </div>
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
        <div className="chat-empty chat-empty--loading">
          {loadingIcon}
          <span>{loadingLabel ?? '正在读取会话…'}</span>
        </div>
      ) : null}
      <div ref={endRef} />
    </div>
  )
}
