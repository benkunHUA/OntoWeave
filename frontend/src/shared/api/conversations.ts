import type { Conversation, ConversationMessage, DeleteScope } from '../types'
import { request } from './client'

export function listConversations(): Promise<Conversation[]> {
  return request<Conversation[]>('/conversations?limit=100')
}

export function createConversation(title?: string): Promise<Conversation> {
  return request<Conversation>('/conversations', {
    method: 'POST',
    body: JSON.stringify({ title: title || null }),
  })
}

export function getConversation(conversationId: string): Promise<Conversation> {
  return request<Conversation>(`/conversations/${conversationId}`)
}

export function renameConversation(
  conversationId: string,
  title: string,
): Promise<Conversation> {
  return request<Conversation>(`/conversations/${conversationId}`, {
    method: 'PATCH',
    body: JSON.stringify({ title }),
  })
}

export function deleteConversation(
  conversationId: string,
  scope: DeleteScope = 'graph-only',
): Promise<void> {
  const query = scope === 'graph-only' ? '' : `?scope=${scope}`
  return request<void>(`/conversations/${conversationId}${query}`, {
    method: 'DELETE',
  })
}

export function listConversationMessages(
  conversationId: string,
): Promise<ConversationMessage[]> {
  return request<ConversationMessage[]>(
    `/conversations/${conversationId}/messages?limit=200`,
  )
}
