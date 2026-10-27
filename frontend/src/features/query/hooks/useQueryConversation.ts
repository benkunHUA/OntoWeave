import { useCallback, useEffect, useRef, useState } from 'react'
import { listConversationMessages } from '../../../shared/api'
import { useAgentRun } from '../../../shared/chat/hooks/useAgentRun'
import { useChatMessages } from '../../../shared/chat/hooks/useChatMessages'
import type { ReportError } from '../../../shared/chat/hooks/useErrorToast'
import { messagesFromHistory } from '../../../shared/chat/lib/chatMessages'
import {
  pendingInterruptFrom,
  type PendingInterrupt,
} from '../../../shared/chat/lib/messageBlocks'

interface UseQueryConversationOptions {
  conversationId: string | undefined
  report: ReportError
  setMessage: (text: string) => void
  clearMessage: () => void
}

/**
 * 查询会话没有 conversation_event 流：只加载历史消息，并用共享的 Agent 回合
 * 处理发送、中断和恢复；每轮结束再重取历史，保证刷新后状态一致。
 */
export function useQueryConversation({
  conversationId,
  report,
  setMessage,
  clearMessage,
}: UseQueryConversationOptions) {
  const chat = useChatMessages()
  const { seed, forget, messagesFor } = chat
  const [isLoading, setIsLoading] = useState(false)
  const [loadedConversationId, setLoadedConversationId] = useState<string | null>(null)
  const [historyInterrupt, setHistoryInterrupt] = useState<PendingInterrupt | null>(null)
  const activeConversationRef = useRef<string | undefined>(conversationId)
  const previousConversationRef = useRef<string | undefined>(undefined)

  const loadHistory = useCallback(
    async (targetId: string) => {
      try {
        const messages = await listConversationMessages(targetId)
        if (activeConversationRef.current !== targetId) return
        seed(targetId, messagesFromHistory(messages))
        setHistoryInterrupt(pendingInterruptFrom(messages))
      } catch (reason) {
        report(reason, '加载问答记录失败')
      }
    },
    [report, seed],
  )

  const onFinished = useCallback(
    (targetId: string) => {
      if (activeConversationRef.current === targetId) void loadHistory(targetId)
    },
    [loadHistory],
  )

  const agent = useAgentRun({
    chat,
    report,
    setMessage,
    clearMessage,
    onFinished,
  })

  const { setInterrupt, reset } = agent
  useEffect(() => {
    setInterrupt(historyInterrupt)
  }, [historyInterrupt, setInterrupt])

  useEffect(() => {
    activeConversationRef.current = conversationId
    const previous = previousConversationRef.current
    previousConversationRef.current = conversationId
    if (previous && previous !== conversationId) forget(previous)
    reset()
    setHistoryInterrupt(null)
    setLoadedConversationId(null)
  }, [conversationId, forget, reset])

  useEffect(() => {
    if (!conversationId) return
    let active = true
    setIsLoading(true)
    void loadHistory(conversationId).finally(() => {
      if (active) {
        setIsLoading(false)
        setLoadedConversationId(conversationId)
      }
    })
    return () => {
      active = false
    }
  }, [conversationId, loadHistory])

  const send = useCallback(
    (text: string) => {
      if (conversationId) void agent.send(conversationId, text)
    },
    [agent, conversationId],
  )

  const answer = useCallback(
    (option: Parameters<typeof agent.answer>[1]) => {
      if (conversationId) void agent.answer(conversationId, option)
    },
    [agent, conversationId],
  )

  return {
    messages: conversationId ? messagesFor(conversationId) : [],
    isLoading,
    loadedConversationId,
    agent,
    send,
    answer,
  }
}
