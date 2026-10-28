import {
  ActionIcon,
  Button,
  Group,
  NavLink,
  ScrollArea,
  Stack,
  Text,
} from '@mantine/core'
import { House, MessageSquareText, Plus, Trash2 } from 'lucide-react'
import { useMemo, useState } from 'react'
import { Brand } from '../../../shared/components/Brand'
import { EmptyState } from '../../../shared/components/EmptyState'
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
      <Group className="query-sidebar__header" justify="space-between" wrap="nowrap">
        <Brand
          compact
          icon={<MessageSquareText size={17} />}
          title={QUERY_COPY.brandSubtitle}
          subtitle={QUERY_COPY.brandTitle}
        />
        <ActionIcon type="button" variant="subtle" color="gray" aria-label="返回首页" onClick={onHome}>
          <House size={17} />
        </ActionIcon>
      </Group>

      <Button
        type="button"
        className="query-new-button"
        fullWidth
        leftSection={<Plus size={16} />}
        onClick={onCreate}
        disabled={isCreating}
      >
        {isCreating ? QUERY_COPY.creating : QUERY_COPY.newConversation}
      </Button>

      <SearchField
        className="query-search"
        value={query}
        onChange={(event) => setQuery(event.target.value)}
        placeholder={QUERY_COPY.searchPlaceholder}
        aria-label={QUERY_COPY.searchPlaceholder}
      />

      <ScrollArea className="query-conversation-list" type="auto" offsetScrollbars>
        <Stack component="nav" aria-label="问答会话列表" gap={4} p="xs">
          {filtered.map((conversation) => {
            const active = conversation.id === activeId
            const title = conversation.title || QUERY_COPY.newConversation
            return (
              <NavLink
                key={conversation.id}
                className="query-conversation-list__item"
                classNames={{ label: 'query-conversation-list__title' }}
                active={active}
                onClick={() => onSelect(conversation)}
                aria-current={active ? 'page' : undefined}
                label={title}
                title={title}
                noWrap
                leftSection={<span className="query-conversation-list__dot" />}
                rightSection={
                  <ActionIcon
                    type="button"
                    className="query-conversation-list__delete"
                    variant="subtle"
                    color="gray"
                    size="sm"
                    aria-label={`删除问答：${title}`}
                    onClick={(event) => {
                      event.stopPropagation()
                      setPendingDelete(conversation)
                    }}
                  >
                    <Trash2 size={14} />
                  </ActionIcon>
                }
              />
            )
          })}
          {isLoading ? (
            <EmptyState className="query-conversation-list__empty" title="正在读取问答会话…" />
          ) : null}
          {!isLoading && !filtered.length ? (
            <EmptyState
              className="query-conversation-list__empty"
              title={QUERY_COPY.listEmpty}
              body={QUERY_COPY.listEmptyHint}
            />
          ) : null}
          {error ? <Text className="query-conversation-list__error" size="xs">{error}</Text> : null}
        </Stack>
      </ScrollArea>

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
