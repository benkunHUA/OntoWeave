import { useCallback } from 'react'
import { useConversationAgent } from '../../../shared/chat/hooks/useConversationAgent'
import type { ReportError } from '../../../shared/chat/hooks/useErrorToast'
import type { StateUpdateEvent } from '../../../shared/types'

interface PipelineConversationOptions {
  conversationId: string | undefined
  report: ReportError
  setMessage: (text: string) => void
  clearMessage: () => void
  onChanged: () => void
  onStateUpdate?: (event: StateUpdateEvent) => void
}

export function usePipelineConversation({
  conversationId,
  report,
  setMessage,
  clearMessage,
  onChanged,
  onStateUpdate,
}: PipelineConversationOptions) {
  const finished = useCallback(() => onChanged(), [onChanged])
  return useConversationAgent({
    conversationId,
    report,
    setMessage,
    clearMessage,
    onFinished: finished,
    onStateUpdate: (event) => {
      onStateUpdate?.(event)
      finished()
    },
  })
}
