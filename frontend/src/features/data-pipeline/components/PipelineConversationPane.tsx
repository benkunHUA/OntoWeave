import { Sparkles, Workflow } from 'lucide-react'
import { useEffect, useMemo, useRef } from 'react'
import { Composer } from '../../../shared/chat/components/Composer'
import { ErrorToast } from '../../../shared/chat/components/ErrorToast'
import { MessageBubble } from '../../../shared/chat/components/MessageBubble'
import type { PendingInterrupt } from '../../../shared/chat/lib/messageBlocks'
import type {
  ChatMessage,
  ConversationEvent,
  InterruptOption,
} from '../../../shared/types'

interface PipelineConversationPaneProps {
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
}

type TimelineItem =
  | { kind: 'message'; at: string; message: ChatMessage }
  | { kind: 'event'; at: string; event: ConversationEvent }

const HIDDEN_EVENT_PREFIXES = ['pipeline.agent.turn', 'pipeline.agent.question']

function visibleEvent(event: ConversationEvent): boolean {
  return !HIDDEN_EVENT_PREFIXES.some((prefix) => event.event_type.startsWith(prefix))
}

function timeOf(value: string): number {
  const parsed = Date.parse(value)
  return Number.isNaN(parsed) ? 0 : parsed
}

export function PipelineConversationPane({
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
}: PipelineConversationPaneProps) {
  const endRef = useRef<HTMLDivElement | null>(null)
  const timeline = useMemo<TimelineItem[]>(() => {
    const items: TimelineItem[] = [
      ...messages.map((message) => ({
        kind: 'message' as const,
        at: message.created_at ?? new Date(0).toISOString(),
        message,
      })),
      ...events.filter(visibleEvent).map((event) => ({
        kind: 'event' as const,
        at: event.created_at,
        event,
      })),
    ]
    return items.sort((left, right) => timeOf(left.at) - timeOf(right.at))
  }, [events, messages])

  const pendingInterruptId = interrupt?.blockId ?? null

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [timeline.length, pendingInterruptId])

  return (
    <section className="chat-pane data-pipeline-chat">
      <header className="chat-header">
        <div>
          <span className="eyebrow">Data pipeline</span>
          <h1>{title || '数据整理助手'}</h1>
        </div>
        <span className={`chat-header__status ${isRunning ? 'is-running' : ''}`}>
          <span />
          {isRunning ? '处理中' : '在线'}
        </span>
      </header>

      <div className="message-scroll">
        {!timeline.length && !isLoading ? (
          <div className="chat-empty">
            <Sparkles size={26} />
            <strong>从一条数据流程开始</strong>
            <p>描述你希望如何清洗数据；也可以直接拖动画布节点，Agent 会同步修改草稿。</p>
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
            <article key={`event-${item.event.id}`} className="pipeline-event-card">
              <span className="pipeline-event-card__icon"><Workflow size={14} /></span>
              <div>
                <strong>{item.event.summary}</strong>
                <time>{new Date(item.event.created_at).toLocaleString()}</time>
              </div>
            </article>
          )
        })}

        {isLoading ? (
          <div className="chat-empty chat-empty--loading">
            <Workflow size={22} />
            <span>正在读取流程会话…</span>
          </div>
        ) : null}
        <div ref={endRef} />
      </div>

      <Composer
        isRunning={isRunning}
        interrupt={interrupt}
        placeholder="描述清洗规则、输出要求或想修改的节点"
        onSend={onSend}
        onAbort={onAbort}
      />
      <ErrorToast message={error} onClose={onClearError} />
    </section>
  )
}
