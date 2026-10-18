import { useCallback, useEffect, useRef, useState } from 'react'
import {
  listConversationEvents,
  listConversationMessages,
  streamConversationEvents,
} from '../../../shared/api'
import { messagesFromHistory } from '../../../shared/chat/lib/chatMessages'
import { pendingInterruptFrom } from '../../../shared/chat/lib/messageBlocks'
import { useAgentRun } from '../../../shared/chat/hooks/useAgentRun'
import { useChatMessages } from '../../../shared/chat/hooks/useChatMessages'
import type { ReportError } from '../../../shared/chat/hooks/useErrorToast'
import type { ConversationEvent } from '../../../shared/types'

const RECONNECT_DELAY_MS = 2_000

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => window.setTimeout(resolve, ms))
}

function appendUnique(current: ConversationEvent[], incoming: ConversationEvent[]): ConversationEvent[] {
  const seen = new Set(current.map((event) => event.id))
  const next = [...current]
  for (const event of incoming) {
    if (!seen.has(event.id)) {
      seen.add(event.id)
      next.push(event)
    }
  }
  return next.sort((left, right) => Date.parse(left.created_at) - Date.parse(right.created_at))
}

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
  const chat = useChatMessages()
  const { seed, forget, messagesFor } = chat
  const [events, setEvents] = useState<ConversationEvent[]>([])
  const [isLoading, setIsLoading] = useState(false)
  const cursorRef = useRef<string | undefined>(undefined)
  const activeConversationRef = useRef<string | undefined>(conversationId)
  const refreshRef = useRef(refreshState)
  const setInterruptRef = useRef<(value: ReturnType<typeof pendingInterruptFrom>) => void>(() => {})

  useEffect(() => {
    refreshRef.current = refreshState
  }, [refreshState])

  const loadHistory = useCallback(
    async (targetId: string) => {
      try {
        const [messages, history] = await Promise.all([
          listConversationMessages(targetId),
          listConversationEvents(targetId),
        ])
        if (activeConversationRef.current !== targetId) return
        seed(targetId, messagesFromHistory(messages))
        setEvents(history)
        cursorRef.current = history.at(-1)?.id
        setInterruptRef.current(pendingInterruptFrom(messages))
      } catch (reason) {
        report(reason, '加载会话记录失败')
      }
    },
    [seed, report],
  )

  const onFinished = useCallback(
    (targetId: string) => {
      if (activeConversationRef.current !== targetId) return
      void loadHistory(targetId)
      void refreshRef.current()
    },
    [loadHistory],
  )

  const agent = useAgentRun({
    chat,
    report,
    setMessage,
    clearMessage,
    onFinished,
    onStateUpdate: () => {
      void refreshRef.current()
    },
  })
  useEffect(() => {
    setInterruptRef.current = agent.setInterrupt
  }, [agent.setInterrupt])

  useEffect(() => {
    const previous = activeConversationRef.current
    activeConversationRef.current = conversationId
    if (previous && previous !== conversationId) {
      forget(previous)
    }
    if (!conversationId) {
      setEvents([])
      cursorRef.current = undefined
      return
    }
    setEvents([])
    cursorRef.current = undefined
    setIsLoading(true)
    void loadHistory(conversationId).finally(() => setIsLoading(false))
  }, [conversationId, forget, loadHistory])

  useEffect(() => {
    if (!conversationId) return
    const targetId = conversationId
    const controller = new AbortController()
    let stopped = false

    async function connect() {
      while (!stopped) {
        try {
          await streamConversationEvents(
            targetId,
            (event) => {
              cursorRef.current = event.id
              setEvents((current) => appendUnique(current, [event]))
              void refreshRef.current()
            },
            controller.signal,
            cursorRef.current,
          )
        } catch {
          if (stopped) return
          await delay(RECONNECT_DELAY_MS)
        }
      }
    }

    void connect()
    return () => {
      stopped = true
      controller.abort()
    }
  }, [conversationId])

  const messages = conversationId ? messagesFor(conversationId) : []

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
    chat,
    messages,
    events,
    isLoading,
    agent,
    send,
    answer,
  }
}
