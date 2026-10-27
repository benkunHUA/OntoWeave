import { useCallback, useEffect, useRef } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'
import { useErrorToast } from '../../../shared/chat/hooks/useErrorToast'
import type { Conversation } from '../../../shared/types'
import { QueryChatPane } from '../components/QueryChatPane'
import { QueryConversationSidebar } from '../components/QueryConversationSidebar'
import { useQueryConversation } from '../hooks/useQueryConversation'
import { useQueryConversations } from '../hooks/useQueryConversations'
import {
  QUERY_DEFAULT_TITLE,
  QUERY_NEW_PATH,
  QUERY_PATH,
} from '../lib/constants'
import { deriveQueryTitle } from '../lib/title'

/** 查询工作台：左侧会话、右侧对话；查询会话不订阅数据连接事件流。 */
export function QueryWorkbench() {
  const { conversationId: routeId } = useParams()
  const { pathname } = useLocation()
  const navigate = useNavigate()
  const toast = useErrorToast()
  const conversations = useQueryConversations(toast.report)
  const isNewPath = pathname === QUERY_NEW_PATH
  const isDraft =
    isNewPath ||
    (!routeId && !conversations.isLoading && conversations.conversations.length === 0)
  const conversationId = isDraft ? undefined : routeId
  const pendingMessageRef = useRef<string | null>(null)
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
    if (isNewPath || routeId || conversations.isLoading) return
    const first = conversations.conversations[0]
    if (first) navigate(`${QUERY_PATH}/${first.id}`, { replace: true })
  }, [isNewPath, routeId, conversations.conversations, conversations.isLoading, navigate])

  const createConversation = useCallback(() => {
    navigate(QUERY_NEW_PATH)
  }, [navigate])

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
      if (!conversationId) {
        if (!isDraft || pendingMessageRef.current) return
        pendingMessageRef.current = text
        void (async () => {
          try {
            const created = await conversations.create.mutateAsync()
            await conversations.rename
              .mutateAsync({ id: created.id, title: deriveQueryTitle(text) })
              .catch(() => undefined)
            navigate(`${QUERY_PATH}/${created.id}`)
          } catch (reason) {
            pendingMessageRef.current = null
            toast.report(reason, '新建问答失败')
          }
        })()
        return
      }
      if (activeConversation && (activeConversation.title || '') === QUERY_DEFAULT_TITLE) {
        void conversations.rename
          .mutateAsync({ id: activeConversation.id, title: deriveQueryTitle(text) })
          .catch(() => undefined)
      }
      conversation.send(text)
    },
    [
      activeConversation,
      conversation,
      conversationId,
      conversations.create,
      conversations.rename,
      isDraft,
      navigate,
      toast,
    ],
  )

  useEffect(() => {
    if (!conversationId || conversation.loadedConversationId !== conversationId) return
    const text = pendingMessageRef.current
    if (!text) return
    pendingMessageRef.current = null
    conversation.send(text)
  }, [conversation, conversation.loadedConversationId, conversationId])

  return (
    <main className="query-page">
      <QueryConversationSidebar
        conversations={conversations.conversations}
        activeId={conversationId ?? null}
        isLoading={conversations.isLoading}
        isCreating={conversations.isCreating}
        isDeleting={conversations.isDeleting}
        error={conversations.listError ? '问答会话加载失败' : null}
        onCreate={createConversation}
        onSelect={(item) => navigate(`${QUERY_PATH}/${item.id}`)}
        onDelete={deleteConversation}
        onHome={() => navigate('/')}
      />
      <div className="query-divider" aria-hidden="true" />
      <QueryChatPane
        title={activeConversation?.title || '业务问答'}
        messages={conversation.messages}
        isRunning={conversation.agent.isRunning}
        isLoading={conversation.isLoading || conversations.isCreating}
        hasConversation={Boolean(conversationId) || isDraft}
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
