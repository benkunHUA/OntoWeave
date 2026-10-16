import { useCallback, useState } from 'react'
import type { ChatMessage, MessageBlock } from '../../../shared/types'

/**
 * 界面上的消息。服务端历史在每次工作区重取时整段覆盖，运行过程中由事件流增量改写——
 * 所以同一份列表既承担历史，也承担正在流式生成的那条。
 */
export function useChatMessages() {
  const [byConversation, setByConversation] = useState<Record<string, ChatMessage[]>>({})

  const seed = useCallback((conversationId: string, messages: ChatMessage[]) => {
    setByConversation((current) => ({ ...current, [conversationId]: messages }))
  }, [])

  const append = useCallback((conversationId: string, message: ChatMessage) => {
    setByConversation((current) => ({
      ...current,
      [conversationId]: [...(current[conversationId] ?? []), message],
    }))
  }, [])

  const patchMessage = useCallback(
    (conversationId: string, messageId: string, update: Partial<ChatMessage>) => {
      setByConversation((current) => ({
        ...current,
        [conversationId]: (current[conversationId] ?? []).map((message) =>
          message.id === messageId ? { ...message, ...update } : message,
        ),
      }))
    },
    [],
  )

  const patchBlocks = useCallback(
    (conversationId: string, messageId: string, update: (blocks: MessageBlock[]) => MessageBlock[]) => {
      setByConversation((current) => ({
        ...current,
        [conversationId]: (current[conversationId] ?? []).map((message) =>
          message.id === messageId ? { ...message, blocks: update(message.blocks) } : message,
        ),
      }))
    },
    [],
  )

  /** 按工具调用 id 改块：续跑后才拿到结果的工具，块留在更早那条消息里。 */
  const patchToolCall = useCallback(
    (
      conversationId: string,
      callId: string,
      update: (blocks: MessageBlock[]) => MessageBlock[],
    ) => {
      setByConversation((current) => ({
        ...current,
        [conversationId]: (current[conversationId] ?? []).map((message) =>
          message.blocks.some((block) => block.kind === 'tool' && block.callId === callId)
            ? { ...message, blocks: update(message.blocks) }
            : message,
        ),
      }))
    },
    [],
  )

  /** 换掉某条消息的 id：服务端在 run.start 里给的是权威身份。 */
  const rekeyMessage = useCallback(
    (conversationId: string, previousId: string, nextId: string) => {
      setByConversation((current) => ({
        ...current,
        [conversationId]: (current[conversationId] ?? []).map((message) =>
          message.id === previousId ? { ...message, id: nextId } : message,
        ),
      }))
    },
    [],
  )

  const forget = useCallback((conversationId: string) => {
    setByConversation((current) => {
      const next = { ...current }
      delete next[conversationId]
      return next
    })
  }, [])

  const messagesFor = useCallback(
    (conversationId: string) => byConversation[conversationId] ?? [],
    [byConversation],
  )

  return {
    messagesFor,
    seed,
    append,
    patchMessage,
    patchBlocks,
    patchToolCall,
    rekeyMessage,
    forget,
  }
}

export type ChatMessages = ReturnType<typeof useChatMessages>
