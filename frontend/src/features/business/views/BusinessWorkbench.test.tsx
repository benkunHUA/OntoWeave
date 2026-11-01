// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '../../../test/render'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import type { Conversation } from '../../../shared/types'
import { QUERY_COPY, QUERY_NEW_PATH } from '../lib/constants'
import { BusinessWorkbench } from './BusinessWorkbench'

const conversationsHook = vi.hoisted(() => ({ useQueryConversations: vi.fn() }))
const conversationHook = vi.hoisted(() => ({ useQueryConversation: vi.fn() }))

vi.mock('../hooks/useQueryConversations', () => conversationsHook)
vi.mock('../hooks/useQueryConversation', () => conversationHook)

const conversations: Conversation[] = [
  { id: 'conversation-1', kind: 'business', resource_id: 'business', title: '第一个会话' },
  { id: 'conversation-2', kind: 'business', resource_id: 'business', title: '第二个会话' },
]

function LocationProbe() {
  return <span data-testid="location">{useLocation().pathname}</span>
}

function renderWorkbench(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <LocationProbe />
      <Routes>
        <Route path="/business/new" element={<BusinessWorkbench />} />
        <Route path="/business/:conversationId" element={<BusinessWorkbench />} />
      </Routes>
    </MemoryRouter>,
  )
}

function expectDraft() {
  expect(screen.getByTestId('location').textContent).toBe(QUERY_NEW_PATH)
  expect(screen.getByRole('heading', { name: QUERY_COPY.brandTitle })).toBeTruthy()
  const composer = screen.getByPlaceholderText(QUERY_COPY.composerPlaceholder) as HTMLTextAreaElement
  expect(composer.disabled).toBe(false)
  expect(screen.getByText(QUERY_COPY.chatEmptyTitle)).toBeTruthy()
}

beforeEach(() => {
  conversationsHook.useQueryConversations.mockReturnValue({
    conversations,
    isLoading: false,
    listError: null,
    isCreating: false,
    isDeleting: false,
    create: { mutateAsync: vi.fn() },
    rename: { mutateAsync: vi.fn() },
    remove: { mutateAsync: vi.fn() },
  })
  conversationHook.useQueryConversation.mockReturnValue({
    messages: [],
    isLoading: false,
    loadedConversationId: null,
    agent: { isRunning: false, interrupt: null, abort: vi.fn() },
    send: vi.fn(),
    answer: vi.fn(),
  })
})

afterEach(cleanup)

describe('BusinessWorkbench new conversation route', () => {
  it('opens an empty draft instead of the current conversation', () => {
    renderWorkbench('/business/conversation-1')

    fireEvent.click(screen.getByRole('button', { name: QUERY_COPY.newConversation }))

    expectDraft()
  })

  it('keeps the new route on refresh instead of redirecting to history', () => {
    renderWorkbench(QUERY_NEW_PATH)

    expectDraft()
  })
})
