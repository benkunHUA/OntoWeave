import { Box } from '@mantine/core'
import { MessageBubble } from '../../../shared/chat/components/MessageBubble'
import type { ChatMessage, InterruptOption } from '../../../shared/types'
import { QUERY_COPY } from '../lib/constants'
import { evidenceGraphFromMessage } from '../lib/evidenceGraph'
import { EvidenceGraph } from './EvidenceGraph'

interface QueryMessageBubbleProps {
  message: ChatMessage
  pendingInterruptId: string | null
  isRunning: boolean
  onAnswer: (option: InterruptOption) => void
  active?: boolean
}

/** 复用共享气泡，只在助手回答下面追加本轮的对象实例子图。 */
export function QueryMessageBubble({
  message,
  pendingInterruptId,
  isRunning,
  onAnswer,
  active = false,
}: QueryMessageBubbleProps) {
  const graph = message.role === 'assistant' ? evidenceGraphFromMessage(message) : null
  return (
    <Box className="query-message">
      <MessageBubble
        message={message}
        pendingInterruptId={pendingInterruptId}
        isRunning={isRunning}
        onAnswer={onAnswer}
        executionLabel={QUERY_COPY.executionTraceTitle}
        active={active}
      />
      {graph ? <EvidenceGraph graph={graph} /> : null}
    </Box>
  )
}
