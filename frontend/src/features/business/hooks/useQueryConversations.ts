import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect } from 'react'
import { createBusinessConversation } from '../../../shared/api'
import {
  CONVERSATION_KIND_BUSINESS,
  type Conversation,
} from '../../../shared/types'
import { deleteConversation, listConversations, renameConversation } from '../../../shared/api'
import type { ReportError } from '../../../shared/chat/hooks/useErrorToast'

const QUERY_CONVERSATION_KEY = ['query-conversations'] as const

/** 业务会话列表：列表、新建、自动改名与删除都在这里维护缓存。 */
export function useQueryConversations(onError: ReportError) {
  const client = useQueryClient()
  const list = useQuery({
    queryKey: QUERY_CONVERSATION_KEY,
    queryFn: () => listConversations(CONVERSATION_KIND_BUSINESS),
  })

  useEffect(() => {
    if (list.error) onError(list.error, '无法加载业务会话')
  }, [list.error, onError])

  const remember = useCallback(
    (conversation: Conversation) => {
      client.setQueryData<Conversation[]>(QUERY_CONVERSATION_KEY, (current) =>
        current?.some((item) => item.id === conversation.id)
          ? current
          : [conversation, ...(current ?? [])],
      )
    },
    [client],
  )

  const create = useMutation({
    mutationFn: createBusinessConversation,
    onSuccess: remember,
  })

  const rename = useMutation({
    mutationFn: ({ id, title }: { id: string; title: string }) =>
      renameConversation(id, title),
    onSuccess: (updated) => {
      client.setQueryData<Conversation[]>(QUERY_CONVERSATION_KEY, (current) =>
        (current ?? []).map((item) => (item.id === updated.id ? updated : item)),
      )
    },
  })

  const remove = useMutation({
    mutationFn: (id: string) => deleteConversation(id),
    onSuccess: (_result, id) => {
      client.setQueryData<Conversation[]>(QUERY_CONVERSATION_KEY, (current) =>
        (current ?? []).filter((item) => item.id !== id),
      )
    },
  })

  return {
    conversations: list.data ?? [],
    isLoading: list.isLoading,
    listError: list.error,
    isCreating: create.isPending,
    isDeleting: remove.isPending,
    create,
    rename,
    remove,
  }
}
