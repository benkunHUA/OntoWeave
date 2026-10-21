import { useCallback, useEffect, useRef, useState } from 'react'
import {
  listConversationEvents,
  listConversationMessages,
  streamConversationEvents,
} from '../../api'
import type { ConversationEvent, StateUpdateEvent } from '../../types'
import { messagesFromHistory } from '../lib/chatMessages'
import { pendingInterruptFrom } from '../lib/messageBlocks'
import type { ReportError } from './useErrorToast'
import { useAgentRun } from './useAgentRun'
import { useChatMessages } from './useChatMessages'

const RECONNECT_DELAY_MS = 2_000

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => window.setTimeout(resolve, ms))
}

function appendUnique(
  current: ConversationEvent[],
  incoming: ConversationEvent[],
): ConversationEvent[] {
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

interface UseConversationAgentOptions {
  conversationId: string | undefined
  report: ReportError
  setMessage: (text: string) => void
  clearMessage: () => void
  onFinished: (conversationId: string) => void
  onStateUpdate?: (event: StateUpdateEvent) => void
}

/** 共享会话壳：历史、事件流、消息与 Agent 回合；领域只提供完成后的刷新逻辑。 */
export function useConversationAgent({
  conversationId,
  report,
  setMessage,
  clearMessage,
  onFinished,
  onStateUpdate,
}: UseConversationAgentOptions) {
  const chat = useChatMessages()
  const { seed, forget, messagesFor } = chat
  const [events, setEvents] = useState<ConversationEvent[]>([])
  const [isLoading, setIsLoading] = useState(false)
  const cursorRef = useRef<string | undefined>(undefined)
  const activeConversationRef = useRef<string | undefined>(conversationId)
  const finishedRef = useRef(onFinished)
  const stateUpdateRef = useRef(onStateUpdate)
  const setInterruptRef = useRef<(value: ReturnType<typeof pendingInterruptFrom>) => void>(
    () => {},
  )
  const resetAgentRef = useRef<() => void>(() => {})

  useEffect(() => {
    finishedRef.current = onFinished
    stateUpdateRef.current = onStateUpdate
  }, [onFinished, onStateUpdate])

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

  const handleFinished = useCallback(
    (targetId: string) => {
      if (activeConversationRef.current !== targetId) return
      void loadHistory(targetId)
      finishedRef.current(targetId)
    },
    [loadHistory],
  )

  const agent = useAgentRun({
    chat,
    report,
    setMessage,
    clearMessage,
    onFinished: handleFinished,
    onStateUpdate: (event) => stateUpdateRef.current?.(event),
  })

  useEffect(() => {
    setInterruptRef.current = agent.setInterrupt
    resetAgentRef.current = agent.reset
  }, [agent.reset, agent.setInterrupt])

  useEffect(() => {
    let active = true
    void (async () => {
      const previous = activeConversationRef.current
      activeConversationRef.current = conversationId
      if (previous !== conversationId) resetAgentRef.current()
      if (previous && previous !== conversationId) forget(previous)
      setEvents([])
      cursorRef.current = undefined
      if (!conversationId) return
      setIsLoading(true)
      try {
        await loadHistory(conversationId)
      } finally {
        if (active) setIsLoading(false)
      }
    })()
    return () => {
      active = false
    }
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
              finishedRef.current(targetId)
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
    events,
    isLoading,
    agent,
    send,
    answer,
  }
}
