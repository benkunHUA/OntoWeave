import { House, MessageSquareText, Plus, Trash2 } from 'lucide-react'
import { useMemo, useState } from 'react'
import { SearchField } from '../../../shared/components/form/controls'
import type { Conversation } from '../../../shared/types'
import { QUERY_COPY } from '../lib/constants'
import { DeleteQueryConversationDialog } from './DeleteQueryConversationDialog'

interface QueryConversationSidebarProps {
  conversations: Conversation[]
  activeId: string | null
  isLoading: boolean
  isCreating: boolean
  isDeleting: boolean
  error: string | null
  onCreate: () => void
  onSelect: (conversation: Conversation) => void
  onDelete: (conversation: Conversation) => Promise<void>
  onHome: () => void
}

/** 左栏：新问答入口、会话搜索、当前用户查询会话列表。 */
export function QueryConversationSidebar({
  conversations,
  activeId,
  isLoading,
  isCreating,
  isDeleting,
  error,
  onCreate,
  onSelect,
  onDelete,
  onHome,
}: QueryConversationSidebarProps) {
  const [query, setQuery] = useState('')
  const [pendingDelete, setPendingDelete] = useState<Conversation | null>(null)

  const filtered = useMemo(() => {
    const keyword = query.trim().toLowerCase()
    if (!keyword) return conversations
    return conversations.filter((item) =>
      (item.title || QUERY_COPY.newConversation).toLowerCase().includes(keyword),
    )
  }, [conversations, query])

  async function confirmDelete() {
    if (!pendingDelete) return
    await onDelete(pendingDelete)
    setPendingDelete(null)
  }

  return (
    <aside className="query-sidebar">
      <header className="query-sidebar__header">
        <div className="brand brand--compact">
          <span className="brand__mark"><MessageSquareText size={17} /></span>
          <div>
            <strong>{QUERY_COPY.brandSubtitle}</strong>
            <span>{QUERY_COPY.brandTitle}</span>
          </div>
        </div>
        <button type="button" className="icon-button icon-button--quiet" aria-label="返回首页" onClick={onHome}>
          <House size={17} />
        </button>
      </header>

      <button
        type="button"
        className="query-new-button"
        onClick={onCreate}
        disabled={isCreating}
      >
        <Plus size={16} />
        {isCreating ? QUERY_COPY.creating : QUERY_COPY.newConversation}
      </button>

      <SearchField
        className="query-search"
        value={query}
        onChange={(event) => setQuery(event.target.value)}
        placeholder={QUERY_COPY.searchPlaceholder}
        aria-label={QUERY_COPY.searchPlaceholder}
      />

      <nav className="query-conversation-list" aria-label="问答会话列表">
        {filtered.map((conversation) => {
          const active = conversation.id === activeId
          const title = conversation.title || QUERY_COPY.newConversation
          return (
            <div
              key={conversation.id}
              className={`query-conversation-list__item ${active ? 'is-active' : ''}`}
            >
              <button
                type="button"
                className="query-conversation-list__select"
                onClick={() => onSelect(conversation)}
                aria-current={active ? 'page' : undefined}
              >
                <span className="query-conversation-list__dot" />
                <span className="query-conversation-list__title">{title}</span>
              </button>
              <button
                type="button"
                className="query-conversation-list__delete"
                aria-label={`删除问答：${title}`}
                onClick={() => setPendingDelete(conversation)}
              >
                <Trash2 size={14} />
              </button>
            </div>
          )
        })}
        {isLoading ? <div className="query-conversation-list__empty">正在读取问答会话…</div> : null}
        {!isLoading && !filtered.length ? (
          <div className="query-conversation-list__empty">
            <strong>{QUERY_COPY.listEmpty}</strong>
            <span>{QUERY_COPY.listEmptyHint}</span>
          </div>
        ) : null}
        {error ? <div className="query-conversation-list__error">{error}</div> : null}
      </nav>

      {pendingDelete ? (
        <DeleteQueryConversationDialog
          title={pendingDelete.title || QUERY_COPY.newConversation}
          isDeleting={isDeleting}
          onCancel={() => setPendingDelete(null)}
          onConfirm={() => void confirmDelete()}
        />
      ) : null}
    </aside>
  )
}
