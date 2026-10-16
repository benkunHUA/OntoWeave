import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect } from 'react'
import type { Conversation, DeleteScope } from '../../../shared/types'
import {
  createConversation,
  deleteConversation,
  listConversations,
  renameConversation,
} from '../../../shared/api'
import type { ReportError } from './useErrorToast'
import { conversationKeys } from './queryKeys'

/**
 * 会话列表：服务端状态放查询缓存，新建 / 改名 / 删掉三个动作用 mutation。
 *
 * mutation 本身不吞错误——弹窗要自己决定失败后停在哪一步，所以调用方 try/catch
 * 后照样交给错误提示条；这里只负责成功后把缓存改对，避免整页重取。
 */
export function useConversations(onError: ReportError) {
  const queryClient = useQueryClient()

  const list = useQuery({ queryKey: conversationKeys.all, queryFn: listConversations })

  useEffect(() => {
    if (list.error) onError(list.error, '无法加载会话')
  }, [list.error, onError])

  const remember = useCallback(
    (conversation: Conversation) => {
      queryClient.setQueryData<Conversation[]>(conversationKeys.all, (current) =>
        current?.some((item) => item.id === conversation.id)
          ? current
          : [conversation, ...(current ?? [])],
      )
    },
    [queryClient],
  )

  const create = useMutation({
    mutationFn: (title?: string) => createConversation(title),
    onSuccess: remember,
  })

  const rename = useMutation({
    mutationFn: ({ id, title }: { id: string; title: string }) =>
      renameConversation(id, title),
    onSuccess: (updated) => {
      queryClient.setQueryData<Conversation[]>(conversationKeys.all, (current) =>
        (current ?? []).map((item) => (item.id === updated.id ? updated : item)),
      )
    },
  })

  const remove = useMutation({
    mutationFn: ({ id, scope }: { id: string; scope: DeleteScope }) =>
      deleteConversation(id, scope),
    onSuccess: (_result, { id }) => {
      queryClient.setQueryData<Conversation[]>(conversationKeys.all, (current) =>
        (current ?? []).filter((item) => item.id !== id),
      )
    },
  })

  return { conversations: list.data ?? [], create, rename, remove, remember }
}
