import { Box } from '@mantine/core'
import { MessageBubble } from '../../../shared/chat/components/MessageBubble'
import type { ChatMessage, InterruptOption } from '../../../shared/types'
import { evidenceFromMessage } from '../lib/evidence'
import { QueryEvidence } from './QueryEvidence'

interface QueryMessageBubbleProps {
  message: ChatMessage
  pendingInterruptId: string | null
  isRunning: boolean
  onAnswer: (option: InterruptOption) => void
}

/** 复用共享气泡，只在助手回答下面追加本次对象查询的证据。 */
export function QueryMessageBubble({
  message,
  pendingInterruptId,
  isRunning,
  onAnswer,
}: QueryMessageBubbleProps) {
  const evidence = message.role === 'assistant' ? evidenceFromMessage(message) : []
  return (
    <Box className="query-message">
      <MessageBubble
        message={message}
        pendingInterruptId={pendingInterruptId}
        isRunning={isRunning}
        onAnswer={onAnswer}
      />
      <QueryEvidence evidence={evidence} />
    </Box>
  )
}
