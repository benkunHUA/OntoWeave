import { useEffect, useRef, useState } from 'react'
import { ensureOntologyConversation } from '../../../shared/api/ontology'
import { useConversationAgent } from '../../../shared/chat/hooks/useConversationAgent'
import type { ReportError } from '../../../shared/chat/hooks/useErrorToast'
import type { OntologyResource } from '../../../shared/types'
import { formatResourceRef } from '../lib/format'

interface OntologyConversationOptions {
  resource: OntologyResource | null
  report: ReportError
  setMessage: (text: string) => void
  clearMessage: () => void
  onFinished?: () => void
}

export function useOntologyConversation({
  resource,
  report,
  setMessage,
  clearMessage,
  onFinished,
}: OntologyConversationOptions) {
  const [conversationId, setConversationId] = useState<string>()
  const [isResolving, setIsResolving] = useState(false)
  const requestRef = useRef(0)
  const resourceKind = resource?.kind
  const resourceId = resource?.id
  const resourceKey = resource ? formatResourceRef(resource) : ''

  useEffect(() => {
    const requestId = ++requestRef.current
    setConversationId(undefined)
    if (!resourceKind || !resourceId) {
      setIsResolving(false)
      return
    }
    setIsResolving(true)
    void ensureOntologyConversation(resourceKind, resourceId)
      .then((conversation) => {
        if (requestRef.current === requestId) setConversationId(conversation.id)
      })
      .catch((reason: unknown) => {
        if (requestRef.current === requestId) report(reason, '打开对象映射会话失败')
      })
      .finally(() => {
        if (requestRef.current === requestId) setIsResolving(false)
      })
    return () => {
      requestRef.current += 1
    }
  }, [report, resourceId, resourceKey, resourceKind])

  const conversation = useConversationAgent({
    conversationId,
    report,
    setMessage,
    clearMessage,
    onFinished: () => onFinished?.(),
  })

  return {
    conversationId,
    messages: conversation.messages,
    events: conversation.events,
    isLoading: isResolving || conversation.isLoading,
    agent: conversation.agent,
    send: conversation.send,
    answer: conversation.answer,
  }
}
