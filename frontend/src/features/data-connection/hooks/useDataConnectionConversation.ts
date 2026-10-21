import { useCallback } from 'react'
import { useConversationAgent } from '../../../shared/chat/hooks/useConversationAgent'
import type { ReportError } from '../../../shared/chat/hooks/useErrorToast'

interface ConversationOptions {
  conversationId: string | undefined
  refreshState: () => Promise<void>
  report: ReportError
  setMessage: (text: string) => void
  clearMessage: () => void
}

export function useDataConnectionConversation({
  conversationId,
  refreshState,
  report,
  setMessage,
  clearMessage,
}: ConversationOptions) {
  const onFinished = useCallback(() => {
    void refreshState()
  }, [refreshState])

  return useConversationAgent({
    conversationId,
    report,
    setMessage,
    clearMessage,
    onFinished,
    onStateUpdate: () => {
      void refreshState()
    },
  })
}
