import { useNavigate } from 'react-router-dom'
import { Composer as ChatComposer } from '../../../shared/chat/components/Composer'
import { useModeling } from '../context/modelingContext'

/** 建模输入区：把通用输入框接到当前会话与智能体回合上。 */
export function Composer() {
  const { workspace, agent, conversations, toast } = useModeling()
  const navigate = useNavigate()
  const conversation = workspace.conversation
  const isCreating = conversations.create.isPending

  return (
    <ChatComposer
      disabled={isCreating}
      isRunning={agent.isRunning}
      interrupt={agent.interrupt}
      onAbort={agent.abort}
      onSend={(text) => {
        if (conversation) {
          void agent.send(conversation.id, text)
          return
        }
        void conversations.create
          .mutateAsync('新对话')
          .then((created) => {
            navigate(`/modeling/${created.id}`, { state: { pendingMessage: text } })
          })
          .catch((reason: unknown) => toast.report(reason, '新建对话失败'))
      }}
    />
  )
}
