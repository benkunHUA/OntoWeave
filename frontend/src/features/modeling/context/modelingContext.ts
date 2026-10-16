import { createContext, useContext } from 'react'
import type { useAgentRun } from '../hooks/useAgentRun'
import type { useActionDrawer } from '../hooks/useActionDrawer'
import type { useCanvasSelection } from '../hooks/useCanvasSelection'
import type { useChatMessages } from '../hooks/useChatMessages'
import type { useConversations } from '../hooks/useConversations'
import type { useErrorToast } from '../hooks/useErrorToast'
import type { useNodeDrawer } from '../hooks/useNodeDrawer'
import type { usePaneLayout } from '../hooks/usePaneLayout'
import type { useRelationDrawer } from '../hooks/useRelationDrawer'
import type { useWorkspace } from '../hooks/useWorkspace'

/**
 * 一个建模页面需要的全部状态与动作，按关注点分组。
 *
 * 分组而不是平铺：面板组件只取自己要的那一组，读起来就知道它依赖什么。
 * 值的类型直接取自各 hook，加字段不用在两处同步。
 */
export interface ModelingContextValue {
  /** 会话列表与增删改。 */
  conversations: ReturnType<typeof useConversations>
  /** 当前会话的工作区：图、视图规格、改动集，以及它们的写操作。 */
  workspace: ReturnType<typeof useWorkspace>
  /** 消息列表（历史 + 正在流式生成的那条）。 */
  chat: ReturnType<typeof useChatMessages>
  /** 智能体回合：发送、回答中断、中止。 */
  agent: ReturnType<typeof useAgentRun>
  /** 画布选中项与派生数据。 */
  selection: ReturnType<typeof useCanvasSelection>
  nodeDrawer: ReturnType<typeof useNodeDrawer>
  relationDrawer: ReturnType<typeof useRelationDrawer>
  /** 选中动作的详情与保存态。 */
  actionDrawer: ReturnType<typeof useActionDrawer>
  /** 会话栏收起状态与两条分隔线的拖拽。 */
  panes: ReturnType<typeof usePaneLayout>
  /** 页面右下角的错误提示。 */
  toast: ReturnType<typeof useErrorToast>
  /** 待确认的字段删除集合，抽屉靠它把待删字段显示成幽灵态。 */
  pendingDeleteIds: ReadonlySet<string>
  /** 改动集摘要的每一句，例如「新增 2」。 */
  opSummary: string[]
  /** 有没有待确认的本体改动。 */
  hasPendingChanges: boolean
  /** 忘掉一个已删除的会话：本地消息与它的工作区缓存一起清掉。 */
  forgetConversation: (conversationId: string) => void
}

export const ModelingContext = createContext<ModelingContextValue | null>(null)

export function useModeling(): ModelingContextValue {
  const value = useContext(ModelingContext)
  if (!value) {
    throw new Error('useModeling 必须在 ModelingProvider 之内使用')
  }
  return value
}
