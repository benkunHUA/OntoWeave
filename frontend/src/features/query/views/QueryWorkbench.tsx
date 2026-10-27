import { useCallback, useEffect } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useErrorToast } from '../../../shared/chat/hooks/useErrorToast'
import type { Conversation } from '../../../shared/types'
import { QueryChatPane } from '../components/QueryChatPane'
import { QueryConversationSidebar } from '../components/QueryConversationSidebar'
import { useQueryConversation } from '../hooks/useQueryConversation'
import { useQueryConversations } from '../hooks/useQueryConversations'
import { QUERY_DEFAULT_TITLE, QUERY_PATH } from '../lib/constants'
import { deriveQueryTitle } from '../lib/title'

/** 查询工作台：左侧会话、右侧对话；查询会话不订阅数据连接事件流。 */
export function QueryWorkbench() {
  const { conversationId } = useParams()
  const navigate = useNavigate()
  const toast = useErrorToast()
  const conversations = useQueryConversations(toast.report)
  const activeConversation = conversations.conversations.find(
    (item) => item.id === conversationId,
  )
  const conversation = useQueryConversation({
    conversationId,
    report: toast.report,
    setMessage: toast.setMessage,
    clearMessage: toast.clear,
  })

  useEffect(() => {
    if (conversationId || conversations.isLoading) return
    const first = conversations.conversations[0]
    if (first) navigate(`${QUERY_PATH}/${first.id}`, { replace: true })
  }, [conversationId, conversations.conversations, conversations.isLoading, navigate])

  const createConversation = useCallback(async () => {
    try {
      const created = await conversations.create.mutateAsync()
      navigate(`${QUERY_PATH}/${created.id}`)
    } catch (reason) {
      toast.report(reason, '新建问答失败')
    }
  }, [conversations.create, navigate, toast])

  const deleteConversation = useCallback(
    async (target: Conversation) => {
      try {
        await conversations.remove.mutateAsync(target.id)
        if (target.id !== conversationId) return
        const next = conversations.conversations.find((item) => item.id !== target.id)
        navigate(next ? `${QUERY_PATH}/${next.id}` : QUERY_PATH, { replace: true })
      } catch (reason) {
        toast.report(reason, '删除问答失败')
      }
    },
    [conversationId, conversations.conversations, conversations.remove, navigate, toast],
  )

  const send = useCallback(
    (text: string) => {
      if (activeConversation && (activeConversation.title || '') === QUERY_DEFAULT_TITLE) {
        void conversations.rename
          .mutateAsync({ id: activeConversation.id, title: deriveQueryTitle(text) })
          .catch(() => undefined)
      }
      conversation.send(text)
    },
    [activeConversation, conversation, conversations.rename],
  )

  return (
    <main className="query-page">
      <QueryConversationSidebar
        conversations={conversations.conversations}
        activeId={conversationId ?? null}
        isLoading={conversations.isLoading}
        isCreating={conversations.isCreating}
        isDeleting={conversations.isDeleting}
        error={conversations.listError ? '问答会话加载失败' : null}
        onCreate={() => void createConversation()}
        onSelect={(item) => navigate(`${QUERY_PATH}/${item.id}`)}
        onDelete={deleteConversation}
        onHome={() => navigate('/')}
      />
      <div className="query-divider" aria-hidden="true" />
      <QueryChatPane
        title={activeConversation?.title || '业务问答'}
        messages={conversation.messages}
        isRunning={conversation.agent.isRunning}
        isLoading={conversation.isLoading}
        hasConversation={Boolean(conversationId)}
        interrupt={conversation.agent.interrupt}
        error={toast.error}
        onClearError={toast.clear}
        onSend={send}
        onAnswer={conversation.answer}
        onAbort={conversation.agent.abort}
      />
    </main>
  )
}
