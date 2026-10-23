// @vitest-environment jsdom

import { act, cleanup, renderHook } from '../../../test/render'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Conversation, OntologyResource } from '../../../shared/types'

const api = vi.hoisted(() => ({ ensureOntologyConversation: vi.fn() }))
const conversationAgent = vi.hoisted(() => ({
  useConversationAgent: vi.fn(),
}))

vi.mock('../../../shared/api/ontology', () => api)
vi.mock('../../../shared/chat/hooks/useConversationAgent', () => conversationAgent)

import { useOntologyConversation } from './useOntologyConversation'

function resource(id: string): OntologyResource {
  return {
    id,
    name: id,
    status: 'confirmed',
    kind: 'object_type',
    binding: null,
    cardinality: null,
    relation_kind: null,
    source_type_id: null,
    source_type_name: null,
    target_type_id: null,
    target_type_name: null,
    forward_label: null,
    reverse_label: null,
    direction_label: null,
    foreign_key_property_id: null,
  }
}

function conversation(id: string): Conversation {
  return { id, kind: 'modeling', resource_id: 'graph-1', title: null }
}

async function settle() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0))
  })
}

describe('useOntologyConversation', () => {
  beforeEach(() => {
    api.ensureOntologyConversation.mockReset()
    conversationAgent.useConversationAgent.mockReset()
    conversationAgent.useConversationAgent.mockReturnValue({
      messages: [],
      events: [],
      isLoading: false,
      agent: { isRunning: false, interrupt: null, abort: vi.fn() },
      send: vi.fn(),
      answer: vi.fn(),
    })
  })

  afterEach(() => {
    cleanup()
  })

  it('ensures a conversation for each resource and drops the stale one immediately', async () => {
    let resolveSecond: (value: Conversation) => void = () => {}
    const report = vi.fn()
    const setMessage = vi.fn()
    const clearMessage = vi.fn()
    api.ensureOntologyConversation
      .mockResolvedValueOnce(conversation('conversation-a'))
      .mockImplementationOnce(() => new Promise((resolve) => {
        resolveSecond = resolve
      }))

    const { result, rerender } = renderHook(
      ({ item }) => useOntologyConversation({
        resource: item,
        report,
        setMessage,
        clearMessage,
      }),
      { initialProps: { item: resource('object-a') } },
    )
    await settle()

    expect(api.ensureOntologyConversation).toHaveBeenCalledWith('object_type', 'object-a')
    expect(result.current.conversationId).toBe('conversation-a')

    rerender({ item: resource('object-b') })
    await settle()
    expect(result.current.conversationId).toBeUndefined()
    expect(api.ensureOntologyConversation).toHaveBeenLastCalledWith('object_type', 'object-b')

    resolveSecond(conversation('conversation-b'))
    await settle()
    expect(result.current.conversationId).toBe('conversation-b')
  })
})
