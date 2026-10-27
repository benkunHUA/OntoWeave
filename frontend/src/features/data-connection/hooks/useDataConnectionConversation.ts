import { useCallback } from 'react'
import { ensureDataConnectionConversation } from '../../../shared/api'
import { useConversationAgent } from '../../../shared/chat/hooks/useConversationAgent'
import type { ReportError } from '../../../shared/chat/hooks/useErrorToast'
import type { Conversation } from '../../../shared/types'

interface ConversationOptions {
  connectionId: string | undefined
  conversationId: string | undefined
  refreshState: () => Promise<void>
  report: ReportError
  setMessage: (text: string) => void
  clearMessage: () => void
}

export function useDataConnectionConversation({
  connectionId,
  conversationId,
  refreshState,
  report,
  setMessage,
  clearMessage,
}: ConversationOptions) {
  const onFinished = useCallback(() => {
    void refreshState()
  }, [refreshState])

  const ensureConversation = useCallback(async (): Promise<Conversation> => {
    if (!connectionId) throw new Error('尚未选择数据连接')
    const conversation = await ensureDataConnectionConversation(connectionId)
    await refreshState()
    return conversation
  }, [connectionId, refreshState])

  return useConversationAgent({
    conversationId,
    ensureConversation: connectionId ? ensureConversation : undefined,
    report,
    setMessage,
    clearMessage,
    onFinished,
    onStateUpdate: () => {
      void refreshState()
    },
  })
}
