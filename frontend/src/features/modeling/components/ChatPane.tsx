import { useEffect, useRef } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useModeling } from '../context/modelingContext'
import { MessageList } from '../../../shared/chat/components/MessageList'
import { ChangesetPanel } from './ChangesetPanel'
import { ChatHeader } from './ChatHeader'
import { Composer } from './Composer'
import { ErrorToast } from './ErrorToast'

/** 中间对话栏：头部、消息、待确认改动条、输入区。状态全在 context 里，这里只摆放。 */
export function ChatPane() {
  const { workspace, chat, agent } = useModeling()
  const conversationId = workspace.conversation?.id
  const messages = conversationId ? chat.messagesFor(conversationId) : []
  const location = useLocation()
  const navigate = useNavigate()
  const pendingMessage = (location.state as { pendingMessage?: string } | null)?.pendingMessage
  const consumedRef = useRef<string | null>(null)

  useEffect(() => {
    if (!pendingMessage || !conversationId || !workspace.workspace || agent.isRunning) return
    const key = `${conversationId}:${pendingMessage}`
    if (consumedRef.current === key) return
    consumedRef.current = key
    navigate(location.pathname, { replace: true, state: null })
    void agent.send(conversationId, pendingMessage)
  }, [
    agent,
    conversationId,
    location.pathname,
    navigate,
    pendingMessage,
    workspace.workspace,
  ])

  return (
    <section className="chat-pane">
      <ChatHeader />
      <MessageList
        messages={messages}
        showEmptyState={!workspace.conversation}
        pendingInterruptId={agent.interrupt?.blockId ?? null}
        isRunning={agent.isRunning}
        onAnswer={(option) => {
          if (conversationId) void agent.answer(conversationId, option)
        }}
      />
      <ChangesetPanel />
      <Composer />
      <ErrorToast />
    </section>
  )
}
