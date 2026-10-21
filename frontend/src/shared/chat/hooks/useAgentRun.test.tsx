// @vitest-environment jsdom

import { act, cleanup, renderHook, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { AgentEvent } from '../../types'
import type { ChatMessages } from './useChatMessages'
import { useAgentRun } from './useAgentRun'

const api = vi.hoisted(() => ({ streamAgentRun: vi.fn() }))

vi.mock('../../../shared/api', () => api)

function fakeChat(): ChatMessages {
  return {
    append: vi.fn(),
    patchBlocks: vi.fn(),
    patchMessage: vi.fn(),
    patchToolCall: vi.fn(),
    rekeyMessage: vi.fn(),
    seed: vi.fn(),
    forget: vi.fn(),
    messagesFor: vi.fn().mockReturnValue([]),
  }
}

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((resolvePromise) => {
    resolve = resolvePromise
  })
  return { promise, resolve }
}

function renderAgent() {
  return renderHook(() => useAgentRun({
    chat: fakeChat(),
    report: vi.fn(),
    setMessage: vi.fn(),
    clearMessage: vi.fn(),
    onFinished: vi.fn(),
  }))
}

describe('useAgentRun turn ids and reset', () => {
  beforeEach(() => api.streamAgentRun.mockReset())
  afterEach(() => cleanup())

  it('generates a fresh turn id for a new message and reuses it for resume', async () => {
    const first = deferred<void>()
    api.streamAgentRun.mockReturnValueOnce(first.promise)
    const { result } = renderAgent()

    let running!: Promise<void>
    act(() => {
      running = result.current.send('conversation-a', 'hello')
    })
    await waitFor(() => expect(api.streamAgentRun).toHaveBeenCalledTimes(1))
    const firstTurn = api.streamAgentRun.mock.calls[0][4]
    expect(firstTurn).toEqual(expect.any(String))

    const onEvent = api.streamAgentRun.mock.calls[0][2] as (event: AgentEvent) => void
    act(() => onEvent({
      v: 1,
      type: 'interrupt',
      messageId: 'message-1',
      blockId: 'block-1',
      question: '继续吗？',
      options: [],
      freeText: { wrap: 'raw' },
    }))
    expect(result.current.interrupt?.question).toBe('继续吗？')

    const second = deferred<void>()
    api.streamAgentRun.mockReturnValueOnce(second.promise)
    let resuming!: Promise<void>
    act(() => {
      resuming = result.current.answer('conversation-a', { label: '继续', answer: 'yes' })
    })
    await waitFor(() => expect(api.streamAgentRun).toHaveBeenCalledTimes(2))
    expect(api.streamAgentRun.mock.calls[1][4]).toBe(firstTurn)

    act(() => {
      first.resolve()
      second.resolve()
    })
    await act(async () => {
      await running
      await resuming
    })
  })

  it('aborts and ignores an old run after reset', async () => {
    const oldRun = deferred<void>()
    const newRun = deferred<void>()
    api.streamAgentRun
      .mockReturnValueOnce(oldRun.promise)
      .mockReturnValueOnce(newRun.promise)
    const { result } = renderAgent()

    let first!: Promise<void>
    act(() => {
      first = result.current.send('conversation-a', 'first')
    })
    await waitFor(() => expect(api.streamAgentRun).toHaveBeenCalledTimes(1))
    const oldSignal = api.streamAgentRun.mock.calls[0][3] as AbortSignal
    const oldOnEvent = api.streamAgentRun.mock.calls[0][2] as (event: AgentEvent) => void

    act(() => oldOnEvent({
      v: 1,
      type: 'interrupt',
      messageId: 'message-old',
      blockId: 'block-old',
      question: '旧中断',
      options: [],
      freeText: null,
    }))
    expect(result.current.interrupt).not.toBeNull()

    act(() => result.current.reset())
    expect(result.current.interrupt).toBeNull()
    expect(result.current.isRunning).toBe(false)
    expect(oldSignal.aborted).toBe(true)

    let second!: Promise<void>
    act(() => {
      second = result.current.send('conversation-b', 'second')
    })
    await waitFor(() => expect(api.streamAgentRun).toHaveBeenCalledTimes(2))
    expect(result.current.isRunning).toBe(true)

    act(() => oldOnEvent({
      v: 1,
      type: 'interrupt',
      messageId: 'message-old',
      blockId: 'block-old',
      question: '旧中断',
      options: [],
      freeText: null,
    }))
    expect(result.current.interrupt).toBeNull()

    act(() => oldRun.resolve())
    await act(async () => {
      await first
    })
    expect(result.current.isRunning).toBe(true)

    act(() => newRun.resolve())
    await act(async () => {
      await second
    })
    expect(result.current.isRunning).toBe(false)
  })
})
