import { Database, Sparkles } from 'lucide-react'
import { useEffect, useMemo, useRef } from 'react'
import { Composer } from '../../../shared/chat/components/Composer'
import { ErrorToast } from '../../../shared/chat/components/ErrorToast'
import { MessageBubble } from '../../../shared/chat/components/MessageBubble'
import type { PendingInterrupt } from '../../../shared/chat/lib/messageBlocks'
import type { ChatMessage, ConversationEvent, InterruptOption } from '../../../shared/types'
import { mergeTimeline } from '../lib/operationTimeline'
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
  const endRef = useRef<HTMLDivElement | null>(null)
  const timeline = useMemo(() => mergeTimeline(messages, events), [messages, events])
  const pendingInterruptId = interrupt?.blockId ?? null

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [timeline.length, pendingInterruptId])

  return (
    <section className="chat-pane data-connection-chat">
      <header className="chat-header">
        <div>
          <span className="eyebrow">Data connection</span>
          <h1>{title || '数据连接助手'}</h1>
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
            <strong>从一个数据源开始</strong>
            <p>在右侧填写连接信息；保存、测试、选表和同步都会回到这里。</p>
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
          <div className="chat-empty chat-empty--loading">
            <Database size={22} />
            <span>正在读取会话记录…</span>
          </div>
        ) : null}
        <div ref={endRef} />
      </div>

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
