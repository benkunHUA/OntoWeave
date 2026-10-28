import { Loader } from '@mantine/core'
import { CircleCheck, Sparkles, Workflow } from 'lucide-react'
import { ChatPaneHeader } from '../../../shared/chat/components/ChatPaneHeader'
import { Composer } from '../../../shared/chat/components/Composer'
import { ConversationTimeline } from '../../../shared/chat/components/ConversationTimeline'
import { ErrorToast } from '../../../shared/chat/components/ErrorToast'
import type { PendingInterrupt } from '../../../shared/chat/lib/messageBlocks'
import type {
  ChatMessage,
  ConversationEvent,
  InterruptOption,
} from '../../../shared/types'
import { isVisiblePipelineEvent } from '../lib/eventVisibility'

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
  return (
    <section className="chat-pane data-pipeline-chat">
      <ChatPaneHeader
        eyebrow="Data pipeline"
        title={title || '数据整理助手'}
        status={isRunning ? '处理中' : '在线'}
        statusColor={isRunning ? 'blue' : 'teal'}
        statusIcon={isRunning ? <Loader size={12} /> : <CircleCheck size={12} />}
      />

      <ConversationTimeline
        messages={messages}
        events={events}
        isRunning={isRunning}
        isLoading={isLoading}
        interrupt={interrupt}
        onAnswer={onAnswer}
        eventFilter={isVisiblePipelineEvent}
        eventIcon={<Workflow size={14} />}
        eventClassName="pipeline-event-card"
        eventIconClassName="pipeline-event-card__icon"
        emptyIcon={<Sparkles size={26} />}
        emptyTitle="从一条数据流程开始"
        emptyBody="描述你希望如何清洗数据；也可以直接拖动画布节点，Agent 会同步修改草稿。"
        loadingIcon={<Workflow size={22} />}
        loadingLabel="正在读取流程会话…"
      />

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
