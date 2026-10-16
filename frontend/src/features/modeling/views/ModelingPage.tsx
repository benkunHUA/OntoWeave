import { ChatPane } from '../components/ChatPane'
import { ConversationPane } from '../components/ConversationPane'
import { GraphPane } from '../components/GraphPane'
import { PaneResizer } from '../components/PaneResizer'
import { ModelingProvider } from '../context/ModelingProvider'
import { useModeling } from '../context/modelingContext'

/**
 * 建模工作台：一整屏三栏。
 *
 * 页面本身只负责摆放，状态与动作都在 ModelingProvider 里；要看某个交互怎么实现，
 * 去对应的面板组件或它下面的 hook。
 */
export function ModelingPage() {
  return (
    <ModelingProvider>
      <ModelingWorkspace />
    </ModelingProvider>
  )
}

function ModelingWorkspace() {
  const { panes } = useModeling()

  return (
    <main
      className={`modeling-page ${panes.collapsed ? 'sidebar-collapsed' : ''}`}
      style={panes.styleVars}
    >
      <ConversationPane />
      <PaneResizer pane="conversations" ariaLabel="拖拽调整会话栏宽度" />
      <ChatPane />
      <PaneResizer pane="chat" ariaLabel="拖拽调整对话栏宽度" />
      <GraphPane />
    </main>
  )
}
