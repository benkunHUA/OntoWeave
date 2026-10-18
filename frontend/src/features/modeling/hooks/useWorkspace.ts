import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useRef } from 'react'
import {
  CONVERSATION_KIND_MODELING,
  type Changeset,
  type Conversation,
  type ConversationMessage,
  type GraphEditOp,
  type GraphSnapshot,
  type ViewSpec,
} from '../../../shared/types'
import {
  confirmChangeset,
  editGraphElements,
  getChangeset,
  getConversation,
  getGraphSnapshot,
  getViewState,
  listChangesets,
  listConversationMessages,
  rejectChangeset,
  saveViewState,
} from '../../../shared/api'
import type { DrawerSubmitOptions } from '../lib/drawerSubmit'
import type { ReportError } from '../../../shared/chat/hooks/useErrorToast'
import { workspaceKeys } from './queryKeys'

/** 一个会话对应的整块工作区：对话、图、视图规格、未定稿的改动集、消息历史。 */
export interface Workspace {
  conversation: Conversation
  snapshot: GraphSnapshot
  viewSpec: ViewSpec
  changeset: Changeset | null
  history: ConversationMessage[]
}

async function fetchWorkspace(conversationId: string): Promise<Workspace> {
  const conversation = await getConversation(conversationId)
  if (conversation.kind !== CONVERSATION_KIND_MODELING) {
    throw new Error('该会话不是建模会话')
  }
  const graphId = conversation.resource_id
  const [snapshot, viewState, changesets, history] = await Promise.all([
    getGraphSnapshot(graphId),
    getViewState(graphId),
    listChangesets(graphId),
    listConversationMessages(conversation.id),
  ])
  const open = changesets.find((item) => item.status === 'open')
  const changeset = open ? await getChangeset(open.id) : null
  return { conversation, snapshot, viewSpec: viewState.spec ?? {}, changeset, history }
}

/**
 * 工作区的服务端状态，全部走查询缓存；本地的即时改动（拖节点、换布局）先改缓存
 * 再防抖落库，所以界面不给服务端等待时间，而服务端最终也会是同一份数据。
 */
export function useWorkspace(conversationId: string | undefined, onError: ReportError) {
  const queryClient = useQueryClient()
  const key = workspaceKeys.detail(conversationId ?? '')
  const saveTimerRef = useRef<number | null>(null)

  const query = useQuery({
    queryKey: key,
    queryFn: () => fetchWorkspace(conversationId as string),
    enabled: Boolean(conversationId),
  })

  useEffect(() => {
    if (query.error) onError(query.error, '无法加载工作区')
  }, [query.error, onError])

  const read = useCallback(
    () => queryClient.getQueryData<Workspace>(key),
    [queryClient, key],
  )

  const patch = useCallback(
    (update: (current: Workspace) => Workspace) => {
      queryClient.setQueryData<Workspace>(key, (current) =>
        current ? update(current) : current,
      )
    },
    [queryClient, key],
  )

  /** 整块重取。确认 / 撤销 / 跑完一轮之后都要走它——那三种情况都会顺手改消息历史。 */
  const refresh = useCallback(
    (targetId?: string) =>
      queryClient.invalidateQueries({
        queryKey: targetId ? workspaceKeys.detail(targetId) : key,
      }),
    [queryClient, key],
  )

  const cancelPendingViewSave = useCallback(() => {
    if (saveTimerRef.current !== null) {
      window.clearTimeout(saveTimerRef.current)
      saveTimerRef.current = null
    }
  }, [])

  const persistView = useCallback(
    (spec: ViewSpec, delay = 0) => {
      const graphId = read()?.conversation.resource_id
      if (!graphId) return
      cancelPendingViewSave()
      saveTimerRef.current = window.setTimeout(() => {
        saveViewState(graphId, spec).catch((reason) => onError(reason, '视图保存失败'))
      }, delay)
    },
    [read, cancelPendingViewSave, onError],
  )

  /** 用户改视图：本地立即生效，随后（或防抖后）落库。 */
  const updateViewSpec = useCallback(
    (spec: ViewSpec, delay = 0) => {
      patch((current) => ({ ...current, viewSpec: spec }))
      persistView(spec, delay)
    },
    [patch, persistView],
  )

  /** 服务端自己改的视图（绘图子智能体）：只同步到本地，别再写回去。 */
  const adoptViewSpec = useCallback(
    (spec: ViewSpec) => patch((current) => ({ ...current, viewSpec: spec })),
    [patch],
  )

  /** 会话改名后同步本地：标题与图名一起改，不用为一次改名整块重取。 */
  const applyConversation = useCallback(
    (conversation: Conversation) =>
      patch((current) => ({
        ...current,
        conversation,
        snapshot: {
          ...current.snapshot,
          graph: { ...current.snapshot.graph, name: conversation.title },
        },
      })),
    [patch],
  )

  const submitEdits = useCallback(
    async (edits: GraphEditOp[], options: DrawerSubmitOptions) => {
      const current = read()
      if (!current) return
      const graphId = current.conversation.resource_id
      const updated = await editGraphElements(graphId, edits)
      if (options.confirm) {
        // 抽屉里直接确认：省掉再去左侧确认条点一次。确认会往会话追一条回执，
        // 所以整块工作区重取一遍，抽屉保持打开、按最新数据重挂。
        await confirmChangeset(updated.id)
        await refresh()
        return
      }
      patch((workspace) => ({ ...workspace, changeset: updated }))
      const snapshot = await getGraphSnapshot(graphId)
      patch((workspace) => ({ ...workspace, snapshot }))
    },
    [read, patch, refresh],
  )

  const confirm = useCallback(async () => {
    const changesetId = read()?.changeset?.id
    if (!changesetId) return
    await confirmChangeset(changesetId)
    await refresh()
  }, [read, refresh])

  const reject = useCallback(async () => {
    const changesetId = read()?.changeset?.id
    if (!changesetId) return
    // 取消还没落库的视图保存：否则撤销把视图翻回去之后，这个 pending 的
    // PUT 又用旧 spec 把它覆盖回来，高亮就「撤销不掉」了。
    cancelPendingViewSave()
    await rejectChangeset(changesetId)
    await refresh()
  }, [read, refresh, cancelPendingViewSave])

  const workspace = query.data ?? null

  return {
    workspace,
    isLoading: query.isPending,
    snapshot: workspace?.snapshot ?? null,
    viewSpec: workspace?.viewSpec ?? {},
    changeset: workspace?.changeset ?? null,
    conversation: workspace?.conversation ?? null,
    history: workspace?.history ?? [],
    updateViewSpec,
    adoptViewSpec,
    applyConversation,
    submitEdits,
    confirm,
    reject,
    refresh,
  }
}
