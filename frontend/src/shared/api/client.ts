import type {
  AgentEvent,
  AgentRunInput,
  Changeset,
  Conversation,
  ConversationMessage,
  GraphSnapshot,
  ViewSpec,
  ViewState,
} from '../types'

const API_BASE = import.meta.env.VITE_API_BASE ?? '/api/v1'

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: {
      'content-type': 'application/json',
      ...init?.headers,
    },
  })
  if (!response.ok) {
    const text = await response.text()
    throw new Error(text || `Request failed: ${response.status}`)
  }
  if (response.status === 204) {
    return undefined as T
  }
  return response.json() as Promise<T>
}

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

export function listConversationMessages(
  conversationId: string,
): Promise<ConversationMessage[]> {
  return request<ConversationMessage[]>(
    `/conversations/${conversationId}/messages?limit=200`,
  )
}

export function getGraphSnapshot(graphId: string): Promise<GraphSnapshot> {
  return request<GraphSnapshot>(`/graphs/${graphId}/snapshot`)
}

export function listChangesets(graphId: string): Promise<Changeset[]> {
  return request<Changeset[]>(`/graphs/${graphId}/changesets?limit=50`)
}

export function getChangeset(changesetId: string): Promise<Changeset> {
  return request<Changeset>(`/changesets/${changesetId}`)
}

export function confirmChangeset(changesetId: string): Promise<Changeset> {
  return request<Changeset>(`/changesets/${changesetId}/confirm`, {
    method: 'POST',
  })
}

export function rejectChangeset(changesetId: string): Promise<Changeset> {
  return request<Changeset>(`/changesets/${changesetId}/reject`, {
    method: 'POST',
  })
}

export function getViewState(graphId: string): Promise<ViewState> {
  return request<ViewState>(`/graphs/${graphId}/view-state`)
}

export function saveViewState(
  graphId: string,
  spec: ViewSpec,
): Promise<ViewState> {
  return request<ViewState>(`/graphs/${graphId}/view-state`, {
    method: 'PUT',
    body: JSON.stringify({ spec }),
  })
}

export async function streamAgentRun(
  conversationId: string,
  body: AgentRunInput,
  onEvent: (event: AgentEvent) => void,
  signal?: AbortSignal,
): Promise<void> {
  const isResume = 'answer' in body
  const suffix = isResume ? 'resume' : 'messages'
  const response = await fetch(
    `${API_BASE}/conversations/${conversationId}/${suffix}`,
    {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify(
        isResume ? { answer: body.answer, text: body.display } : { text: body.text },
      ),
      signal,
    },
  )
  if (!response.ok) {
    throw new Error((await response.text()) || `Agent run failed: ${response.status}`)
  }
  if (!response.body) {
    throw new Error('Agent stream returned no body')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const blocks = buffer.split('\n\n')
    buffer = blocks.pop() ?? ''
    for (const block of blocks) {
      const data = block
        .split('\n')
        .filter((line) => line.startsWith('data: '))
        .map((line) => line.slice(6))
        .join('\n')
      if (data) {
        onEvent(JSON.parse(data) as AgentEvent)
      }
    }
  }

  if (buffer.trim()) {
    for (const line of buffer.split('\n')) {
      if (line.startsWith('data: ')) {
        onEvent(JSON.parse(line.slice(6)) as AgentEvent)
      }
    }
  }
}
