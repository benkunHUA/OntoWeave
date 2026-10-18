import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useMemo, type ReactNode } from 'react'
import { useParams } from 'react-router-dom'
import { messagesFromHistory } from '../../../shared/chat/lib/chatMessages'
import { formatOpSummary, hasPendingOntologyChanges } from '../lib/changesetSummary'
import { pendingInterruptFrom } from '../../../shared/chat/lib/messageBlocks'
import { pendingDeleteIds } from '../lib/pendingDeletes'
import { useActionDrawer } from '../hooks/useActionDrawer'
import { useAgentRun } from '../../../shared/chat/hooks/useAgentRun'
import { useCanvasSelection } from '../hooks/useCanvasSelection'
import { useChatMessages } from '../../../shared/chat/hooks/useChatMessages'
import { useConversations } from '../hooks/useConversations'
import { useErrorToast } from '../../../shared/chat/hooks/useErrorToast'
import { useNodeDrawer } from '../hooks/useNodeDrawer'
import { usePaneLayout } from '../hooks/usePaneLayout'
import { useRelationDrawer } from '../hooks/useRelationDrawer'
import { useWorkspace } from '../hooks/useWorkspace'
import { workspaceKeys } from '../hooks/queryKeys'
import { ModelingContext, type ModelingContextValue } from './modelingContext'

/**
 * 建模页面的装配点：把各关注点的 hook 接起来，往下只提供一份上下文。
 *
 * 装配本身不做业务判断——谁依赖谁在这里写清楚，规则留在各自的 hook 与 lib 里。
 */
export function ModelingProvider({ children }: { children: ReactNode }) {
  const { conversationId } = useParams()
  const queryClient = useQueryClient()
  const toast = useErrorToast()
  const conversations = useConversations(toast.report)
  const workspace = useWorkspace(conversationId, toast.report)
  const chat = useChatMessages()
  const selection = useCanvasSelection(workspace.snapshot)

  const refreshWorkspace = workspace.refresh
  const adoptViewSpec = workspace.adoptViewSpec
  const onRunFinished = useCallback(
    (targetId: string) => {
      void refreshWorkspace(targetId)
    },
    [refreshWorkspace],
  )

  const agent = useAgentRun({
    chat,
    report: toast.report,
    setMessage: toast.setMessage,
    clearMessage: toast.clear,
    onFinished: onRunFinished,
    onStateUpdate: (event) => {
      if (event.view && typeof event.view === 'object') {
        adoptViewSpec(event.view)
      }
    },
  })

  const nodeDrawer = useNodeDrawer({
    node: selection.selectedNode,
    submitEdits: workspace.submitEdits,
  })
  const relationDrawer = useRelationDrawer({
    relation: selection.selectedRelation,
    submitEdits: workspace.submitEdits,
  })
  const actionDrawer = useActionDrawer({
    actionId: selection.selectedActionId,
    snapshot: workspace.snapshot,
    submitEdits: workspace.submitEdits,
  })
  const panes = usePaneLayout()

  const actionLoadError = actionDrawer.loadError
  const reportError = toast.report
  useEffect(() => {
    if (actionLoadError) reportError(actionLoadError, '无法读取动作定义')
  }, [actionLoadError, reportError])

  const loaded = workspace.workspace
  const seed = chat.seed
  const remember = conversations.remember
  const setInterrupt = agent.setInterrupt
  useEffect(() => {
    if (!loaded) return
    // 服务端是消息的唯一权威：每次工作区重取都整段覆盖本地那条会话的消息，
    // 并顺带恢复「还停在等人回答」的状态，刷新后用户不用重新问一遍。
    seed(loaded.conversation.id, messagesFromHistory(loaded.history))
    setInterrupt(pendingInterruptFrom(loaded.history))
    remember(loaded.conversation)
  }, [loaded, seed, setInterrupt, remember])

  const forget = chat.forget
  const forgetConversation = useCallback(
    (targetId: string) => {
      forget(targetId)
      queryClient.removeQueries({ queryKey: workspaceKeys.detail(targetId) })
    },
    [forget, queryClient],
  )

  const changeset = workspace.changeset
  const pendingDeletes = useMemo(() => pendingDeleteIds(changeset), [changeset])
  const opSummary = useMemo(() => formatOpSummary(changeset), [changeset])

  const value: ModelingContextValue = {
    conversations,
    workspace,
    chat,
    agent,
    selection,
    nodeDrawer,
    relationDrawer,
    actionDrawer,
    panes,
    toast,
    pendingDeleteIds: pendingDeletes,
    opSummary,
    hasPendingChanges: hasPendingOntologyChanges(changeset),
    forgetConversation,
  }

  return <ModelingContext.Provider value={value}>{children}</ModelingContext.Provider>
}
