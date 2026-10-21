import { useCallback, useRef, useState } from 'react'
import type {
  AgentEvent,
  AgentRunInput,
  InterruptOption,
  StateUpdateEvent,
} from '../../../shared/types'
import { streamAgentRun } from '../../../shared/api'
import { newId, userMessage } from '../lib/chatMessages'
import { reduceBlocks, type PendingInterrupt } from '../lib/messageBlocks'
import { resolveTurnId } from '../lib/turnId'
import type { ChatMessages } from './useChatMessages'
import type { ReportError } from './useErrorToast'

interface UseAgentRunOptions {
  chat: ChatMessages
  report: ReportError
  /** 把失败文案同时推给错误提示条。 */
  setMessage: (text: string) => void
  clearMessage: () => void
  /** 跑完一轮后重取工作区：本轮改动、回执与新消息都在服务端。 */
  onFinished: (conversationId: string) => void
  /** `state.update` 由各领域自行消费：建模同步视图规格，数据连接刷新状态。 */
  onStateUpdate?: (event: StateUpdateEvent) => void
}

/**
 * 一次智能体回合：把 SSE 事件流折进消息块，跑完再重取工作区对齐服务端。
 *
 * 消息 id 以服务端为准——本地先占位，run.start 一到就换成服务端的，续跑与刷新才对得上。
 */
export function useAgentRun({
  chat,
  report,
  setMessage,
  clearMessage,
  onFinished,
  onStateUpdate,
}: UseAgentRunOptions) {
  const [isRunning, setIsRunning] = useState(false)
  const [interrupt, setInterrupt] = useState<PendingInterrupt | null>(null)
  const abortRef = useRef<AbortController | null>(null)
  const turnIdRef = useRef<string | null>(null)
  const runTokenRef = useRef(0)

  const run = useCallback(
    async (conversationId: string, body: AgentRunInput, turnId: string) => {
      const runToken = runTokenRef.current + 1
      runTokenRef.current = runToken
      const isCurrent = () => runTokenRef.current === runToken
      turnIdRef.current = turnId
      let activeId = newId()
      chat.append(conversationId, {
        id: activeId,
        role: 'assistant',
        blocks: [],
        created_at: new Date().toISOString(),
      })
      setIsRunning(true)
      setInterrupt(null)
      clearMessage()
      const controller = new AbortController()
      abortRef.current = controller

      function failWith(text: string) {
        chat.patchBlocks(conversationId, activeId, () => [
          { id: newId(), kind: 'text', text, done: true },
        ])
        chat.patchMessage(conversationId, activeId, { kind: 'error' })
        setMessage(text)
      }

      function handleEvent(event: AgentEvent) {
        if (!isCurrent()) return
        if (event.type === 'run.start') {
          // 服务端分配的消息 id 才是权威身份，用它替掉本地占位 id。
          const serverId = event.messageId
          if (serverId && serverId !== activeId) {
            chat.rekeyMessage(conversationId, activeId, serverId)
            activeId = serverId
          }
        } else if (event.type === 'state.update') {
          onStateUpdate?.(event)
        } else if (event.type === 'run.error') {
          failWith(String(event.message ?? '运行失败'))
        } else if (event.type === 'interrupt') {
          chat.patchBlocks(conversationId, activeId, (blocks) => reduceBlocks(blocks, event))
          setInterrupt({
            blockId: event.blockId,
            question: event.question ?? '需要你确认一下',
            options: event.options ?? [],
            freeText: event.freeText ?? null,
          })
        } else if (event.type === 'tool.end') {
          chat.patchToolCall(conversationId, event.callId, (blocks) =>
            reduceBlocks(blocks, event),
          )
        } else if (
          event.type === 'block.start' ||
          event.type === 'block.delta' ||
          event.type === 'block.end' ||
          event.type === 'tool.start'
        ) {
          chat.patchBlocks(conversationId, activeId, (blocks) => reduceBlocks(blocks, event))
        }
      }

      try {
        await streamAgentRun(conversationId, body, handleEvent, controller.signal, turnId)
      } catch (reason) {
        if (isCurrent() && !controller.signal.aborted) {
          report(reason, '运行失败')
        }
      } finally {
        if (isCurrent()) {
          setIsRunning(false)
          abortRef.current = null
          onFinished(conversationId)
        }
      }
    },
    [chat, report, setMessage, clearMessage, onFinished, onStateUpdate],
  )

  /** 用户发一句话：停在中断上时，这句话就是那次中断的回答。 */
  const send = useCallback(
    async (conversationId: string, text: string) => {
      const trimmed = text.trim()
      if (!trimmed) return
      if (interrupt) {
        if (!interrupt.freeText) return
        const turnId = resolveTurnId(turnIdRef.current, true)
        chat.append(conversationId, userMessage(trimmed))
        await run(
          conversationId,
          {
            answer:
              interrupt.freeText.wrap === 'respond'
                ? { decisions: [{ type: 'respond', message: trimmed }] }
                : trimmed,
            display: trimmed,
          },
          turnId,
        )
        return
      }
      const turnId = resolveTurnId(turnIdRef.current, false)
      chat.append(conversationId, userMessage(trimmed))
      await run(conversationId, { text: trimmed }, turnId)
    },
    [chat, interrupt, run],
  )

  /** 用户点了中断卡片上的选项。 */
  const answer = useCallback(
    async (conversationId: string, option: InterruptOption) => {
      const turnId = resolveTurnId(turnIdRef.current, true)
      chat.append(conversationId, userMessage(option.label))
      await run(conversationId, { answer: option.answer, display: option.label }, turnId)
    },
    [chat, run],
  )

  const abort = useCallback(() => abortRef.current?.abort(), [])

  const reset = useCallback(() => {
    runTokenRef.current += 1
    abortRef.current?.abort()
    abortRef.current = null
    turnIdRef.current = null
    setIsRunning(false)
    setInterrupt(null)
  }, [])

  return { isRunning, interrupt, setInterrupt, run, send, answer, abort, reset }
}
