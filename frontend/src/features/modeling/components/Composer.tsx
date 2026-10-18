import { Composer as ChatComposer } from '../../../shared/chat/components/Composer'
import { useModeling } from '../context/modelingContext'

/** 建模输入区：把通用输入框接到当前会话与智能体回合上。 */
export function Composer() {
  const { workspace, agent } = useModeling()
  const conversation = workspace.conversation

  return (
    <ChatComposer
      disabled={!conversation}
      isRunning={agent.isRunning}
      interrupt={agent.interrupt}
      onAbort={agent.abort}
      onSend={(text) => {
        if (conversation) void agent.send(conversation.id, text)
      }}
    />
  )
}
