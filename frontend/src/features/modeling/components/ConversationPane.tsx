import { MessageSquarePlus, PanelLeftClose, PanelLeftOpen, Sparkles, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  CONVERSATION_KIND_MODELING,
  type Conversation,
  type DeleteScope,
} from '../../../shared/types'
import { useModeling } from '../context/modelingContext'
import { DEFAULT_DELETE_SCOPE } from '../lib/deleteScopes'
import { DeleteConversationDialog } from './dialogs/DeleteConversationDialog'

/** 左侧会话栏：列表、新建、删除，以及整栏的收起。 */
export function ConversationPane() {
  const { conversations, workspace, panes, toast, forgetConversation } = useModeling()
  const navigate = useNavigate()
  const [pendingDelete, setPendingDelete] = useState<Conversation | null>(null)
  const [deleteMode, setDeleteMode] = useState<DeleteScope>(DEFAULT_DELETE_SCOPE)
  const [isDeleting, setIsDeleting] = useState(false)

  const activeId = workspace.conversation?.id ?? null
  const modelingConversations = conversations.conversations.filter(
    (conversation) => conversation.kind === CONVERSATION_KIND_MODELING,
  )

  async function startConversation() {
    try {
      const conversation = await conversations.create.mutateAsync('新对话')
      navigate(`/modeling/${conversation.id}`)
    } catch (reason) {
      toast.report(reason, '新建对话失败')
    }
  }

  function openDeleteDialog(conversation: Conversation) {
    setDeleteMode(DEFAULT_DELETE_SCOPE)
    setPendingDelete(conversation)
  }

  async function confirmDelete() {
    const target = pendingDelete
    if (!target) return
    setIsDeleting(true)
    try {
      await conversations.remove.mutateAsync({ id: target.id, scope: deleteMode })
      forgetConversation(target.id)
      setPendingDelete(null)
      if (activeId === target.id) {
        navigate('/')
      }
    } catch (reason) {
      toast.report(reason, '删除失败')
    } finally {
      setIsDeleting(false)
    }
  }

  return (
    <aside className="conversation-pane">
      <header className="conversation-pane__header">
        <div className="brand brand--compact">
          <span className="brand__mark">
            <Sparkles size={17} />
          </span>
          {!panes.collapsed ? (
            <div>
              <strong>OntoWeave</strong>
              <span>建模工作台</span>
            </div>
          ) : null}
        </div>
        <button
          type="button"
          className="icon-button icon-button--quiet"
          aria-label={panes.collapsed ? '展开会话栏' : '收起会话栏'}
          onClick={panes.toggleCollapsed}
        >
          {panes.collapsed ? <PanelLeftOpen size={18} /> : <PanelLeftClose size={18} />}
        </button>
      </header>

      <button
        type="button"
        className="new-conversation-button"
        onClick={() => void startConversation()}
      >
        <MessageSquarePlus size={17} />
        {!panes.collapsed ? '新建对话' : null}
      </button>

      <nav className="conversation-list" aria-label="会话列表">
        {modelingConversations.map((conversation) => (
          <div
            key={conversation.id}
            className={`conversation-list__item ${
              conversation.id === activeId ? 'is-active' : ''
            }`}
          >
            <button
              type="button"
              className="conversation-list__open"
              onClick={() => navigate(`/modeling/${conversation.id}`)}
              title={conversation.title || '未命名对话'}
            >
              <span className="conversation-list__dot" />
              {!panes.collapsed ? (
                <span className="conversation-list__title">
                  {conversation.title || '未命名对话'}
                </span>
              ) : null}
            </button>
            {!panes.collapsed ? (
              <button
                type="button"
                className="conversation-list__delete"
                aria-label={`删除对话 ${conversation.title || '未命名对话'}`}
                title="删除对话"
                onClick={() => openDeleteDialog(conversation)}
              >
                <Trash2 size={14} />
              </button>
            ) : null}
          </div>
        ))}
      </nav>

      {pendingDelete ? (
        <DeleteConversationDialog
          conversationTitle={pendingDelete.title}
          scope={deleteMode}
          isDeleting={isDeleting}
          onScopeChange={setDeleteMode}
          onCancel={() => setPendingDelete(null)}
          onConfirm={() => void confirmDelete()}
        />
      ) : null}
    </aside>
  )
}
