// @vitest-environment jsdom

import { act, cleanup, renderHook, waitFor } from '../../../test/render'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { AgentEvent, Conversation } from '../../types'
import { useConversationAgent } from './useConversationAgent'

const api = vi.hoisted(() => ({
  listConversationEvents: vi.fn(),
  listConversationMessages: vi.fn(),
  streamAgentRun: vi.fn(),
  streamConversationEvents: vi.fn(),
}))

vi.mock('../../api', () => api)

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((resolvePromise) => {
    resolve = resolvePromise
  })
  return { promise, resolve }
}

describe('useConversationAgent conversation switches', () => {
  beforeEach(() => {
    api.listConversationMessages.mockResolvedValue([])
    api.listConversationEvents.mockResolvedValue([])
    api.streamConversationEvents.mockImplementation(
      (_id: string, _onEvent: unknown, signal?: AbortSignal) =>
        new Promise<void>((_resolve, reject) => {
          signal?.addEventListener('abort', () => reject(new Error('aborted')))
        }),
    )
  })

  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
  })

  it('aborts the old run and clears its interrupt when the conversation changes', async () => {
    const oldRun = deferred<void>()
    api.streamAgentRun.mockReturnValueOnce(oldRun.promise)
    const report = vi.fn()
    const setMessage = vi.fn()
    const clearMessage = vi.fn()
    const onFinished = vi.fn()

    const { result, rerender } = renderHook(
      ({ conversationId }: { conversationId: string }) =>
        useConversationAgent({
          conversationId,
          report,
          setMessage,
          clearMessage,
          onFinished,
        }),
      { initialProps: { conversationId: 'conversation-a' } },
    )

    let running!: Promise<void>
    act(() => {
      running = result.current.agent.send('conversation-a', 'hello')
    })
    await waitFor(() => expect(api.streamAgentRun).toHaveBeenCalledTimes(1))
    const oldSignal = api.streamAgentRun.mock.calls[0][3] as AbortSignal
    const onEvent = api.streamAgentRun.mock.calls[0][2] as (event: AgentEvent) => void

    act(() => onEvent({
      v: 1,
      type: 'interrupt',
      messageId: 'message-a',
      blockId: 'block-a',
      question: '旧流程中断',
      options: [],
      freeText: null,
    }))
    expect(result.current.agent.interrupt?.question).toBe('旧流程中断')

    rerender({ conversationId: 'conversation-b' })

    await waitFor(() => expect(result.current.agent.interrupt).toBeNull())
    expect(result.current.agent.isRunning).toBe(false)
    expect(oldSignal.aborted).toBe(true)

    act(() => onEvent({
      v: 1,
      type: 'interrupt',
      messageId: 'message-a',
      blockId: 'block-a',
      question: '不应出现',
      options: [],
      freeText: null,
    }))
    expect(result.current.agent.interrupt).toBeNull()

    act(() => oldRun.resolve())
    await act(async () => {
      await running
    })
  })

  it('creates a conversation only when the first message is sent', async () => {
    api.streamAgentRun.mockResolvedValue(undefined)
    const report = vi.fn()
    const ensureConversation = vi.fn().mockResolvedValue({
      id: 'conversation-new',
      kind: 'modeling',
      resource_id: 'resource-new',
      title: '新对话',
    } satisfies Conversation)

    const { result } = renderHook(() =>
      useConversationAgent({
        conversationId: undefined,
        ensureConversation,
        report,
        setMessage: vi.fn(),
        clearMessage: vi.fn(),
        onFinished: vi.fn(),
      }),
    )

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(ensureConversation).not.toHaveBeenCalled()

    act(() => result.current.send('第一句话'))

    await waitFor(() => expect(ensureConversation).toHaveBeenCalledTimes(1))
    await waitFor(() => expect(api.streamAgentRun).toHaveBeenCalledTimes(1))
    expect(api.listConversationMessages).toHaveBeenCalledWith('conversation-new')
    expect(api.streamAgentRun.mock.calls[0][0]).toBe('conversation-new')
    expect(api.streamAgentRun.mock.calls[0][1]).toMatchObject({ text: '第一句话' })
  })
})
