import { useCallback, useEffect, useRef, useState } from 'react'
import {
  ensurePipelineConversation,
  getPipelineConversation,
} from '../../../shared/api'
import { useConversationAgent } from '../../../shared/chat/hooks/useConversationAgent'
import type { ReportError } from '../../../shared/chat/hooks/useErrorToast'
import type { Conversation, StateUpdateEvent } from '../../../shared/types'

interface PipelineConversationOptions {
  pipelineId: string | undefined
  report: ReportError
  setMessage: (text: string) => void
  clearMessage: () => void
  onChanged: () => void
  onStateUpdate?: (event: StateUpdateEvent) => void
}

export function usePipelineConversation({
  pipelineId,
  report,
  setMessage,
  clearMessage,
  onChanged,
  onStateUpdate,
}: PipelineConversationOptions) {
  const [conversationId, setConversationId] = useState<string | undefined>()
  const lookupRef = useRef(0)

  useEffect(() => {
    let active = true
    const requestId = ++lookupRef.current
    setConversationId(undefined)
    if (!pipelineId) return
    void getPipelineConversation(pipelineId)
      .then((conversation) => {
        if (active && lookupRef.current === requestId) {
          setConversationId(conversation?.id)
        }
      })
      .catch((reason: unknown) => {
        if (active && lookupRef.current === requestId) {
          report(reason, '加载流程会话失败')
        }
      })
    return () => {
      active = false
    }
  }, [pipelineId, report])

  const ensureConversation = useCallback(async (): Promise<Conversation> => {
    if (!pipelineId) throw new Error('尚未选择数据流程')
    lookupRef.current += 1
    const conversation = await ensurePipelineConversation(pipelineId)
    setConversationId(conversation.id)
    return conversation
  }, [pipelineId])

  const finished = useCallback(() => onChanged(), [onChanged])
  const conversation = useConversationAgent({
    conversationId,
    ensureConversation: pipelineId ? ensureConversation : undefined,
    report,
    setMessage,
    clearMessage,
    onFinished: finished,
    onStateUpdate: (event) => {
      onStateUpdate?.(event)
      finished()
    },
  })
  return conversation
}
