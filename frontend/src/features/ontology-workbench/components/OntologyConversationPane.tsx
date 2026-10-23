import { Network, Sparkles } from 'lucide-react'
import { Composer } from '../../../shared/chat/components/Composer'
import { ConversationTimeline } from '../../../shared/chat/components/ConversationTimeline'
import { ErrorToast } from '../../../shared/chat/components/ErrorToast'
import type { PendingInterrupt } from '../../../shared/chat/lib/messageBlocks'
import type {
  ChatMessage,
  ConversationEvent,
  InterruptOption,
  OntologyResource,
} from '../../../shared/types'
import { CONVERSATION_COPY } from '../lib/constants'
import { isVisibleOntologyEvent } from '../lib/format'

interface OntologyConversationPaneProps {
  resource: OntologyResource | null
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

export function OntologyConversationPane({
  resource,
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
}: OntologyConversationPaneProps) {
  return (
    <section className="chat-pane ontology-chat-pane">
      <header className="chat-header">
        <div>
          <span className="eyebrow">{CONVERSATION_COPY.eyebrow}</span>
          <h1>{resource ? `${resource.name} 映射` : CONVERSATION_COPY.title}</h1>
        </div>
        <span className={`chat-header__status ${isRunning ? 'is-running' : ''}`}>
          <span />
          {isRunning ? '处理中' : '在线'}
        </span>
      </header>

      <ConversationTimeline
        messages={messages}
        events={events}
        isRunning={isRunning}
        isLoading={isLoading}
        interrupt={interrupt}
        onAnswer={onAnswer}
        eventFilter={isVisibleOntologyEvent}
        eventIcon={<Network size={14} />}
        eventClassName="ontology-event-card"
        eventIconClassName="ontology-event-card__icon"
        emptyIcon={<Sparkles size={26} />}
        emptyTitle={CONVERSATION_COPY.emptyTitle}
        emptyBody={CONVERSATION_COPY.emptyBody}
        loadingIcon={<Network size={22} />}
        loadingLabel="正在读取映射会话…"
      />

      <Composer
        isRunning={isRunning}
        interrupt={interrupt}
        placeholder={CONVERSATION_COPY.placeholder}
        onSend={onSend}
        onAbort={onAbort}
      />
      <ErrorToast message={error} onClose={onClearError} />
    </section>
  )
}
