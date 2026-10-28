import {
  ActionIcon,
  Button,
  Group,
  ScrollArea,
  Stack,
  Text,
  ThemeIcon,
  UnstyledButton,
} from '@mantine/core'
import { MessageSquarePlus, PanelLeftClose, PanelLeftOpen, Sparkles, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { EmptyState } from '../../../shared/components/EmptyState'
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

  function startConversation() {
    navigate('/modeling')
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
      <Group className="conversation-pane__header" justify="space-between" wrap="nowrap">
        {panes.collapsed ? (
          <ThemeIcon size={34} radius="md" variant="light" color="brand" aria-label="OntoWeave 建模工作台">
            <Sparkles size={17} />
          </ThemeIcon>
        ) : (
          <Group gap={8} wrap="nowrap">
            <ThemeIcon size={34} radius="md" variant="light" color="brand" aria-hidden="true">
              <Sparkles size={17} />
            </ThemeIcon>
            <Stack gap={1}>
              <Text fw={700} size="sm" lh={1.2}>OntoWeave</Text>
              <Text size="xs" c="dimmed" lh={1.2}>建模工作台</Text>
            </Stack>
          </Group>
        )}
        <ActionIcon
          type="button"
          variant="subtle"
          color="gray"
          aria-label={panes.collapsed ? '展开会话栏' : '收起会话栏'}
          onClick={panes.toggleCollapsed}
        >
          {panes.collapsed ? <PanelLeftOpen size={18} /> : <PanelLeftClose size={18} />}
        </ActionIcon>
      </Group>

      <Button
        type="button"
        className="new-conversation-button"
        fullWidth={!panes.collapsed}
        aria-label="新建对话"
        leftSection={<MessageSquarePlus size={17} />}
        onClick={startConversation}
      >
        {!panes.collapsed ? '新建对话' : null}
      </Button>

      <ScrollArea className="conversation-list" type="auto" offsetScrollbars>
        <Stack component="nav" aria-label="会话列表" gap={4} p="xs">
          {modelingConversations.map((conversation) => (
            <Group
              key={conversation.id}
              className={`conversation-list__item ${
                conversation.id === activeId ? 'is-active' : ''
              }`}
              gap={2}
              wrap="nowrap"
            >
              <UnstyledButton
                type="button"
                className="conversation-list__open"
                onClick={() => navigate(`/modeling/${conversation.id}`)}
                title={conversation.title || '未命名对话'}
              >
                <span className="conversation-list__dot" />
                {!panes.collapsed ? (
                  <Text className="conversation-list__title" component="span" size="xs" truncate>
                    {conversation.title || '未命名对话'}
                  </Text>
                ) : null}
              </UnstyledButton>
              {!panes.collapsed ? (
                <ActionIcon
                  type="button"
                  className="conversation-list__delete"
                  variant="subtle"
                  color="gray"
                  size="sm"
                  aria-label={`删除对话 ${conversation.title || '未命名对话'}`}
                  title="删除对话"
                  onClick={() => openDeleteDialog(conversation)}
                >
                  <Trash2 size={14} />
                </ActionIcon>
              ) : null}
            </Group>
          ))}
          {!modelingConversations.length ? (
            <EmptyState title={panes.collapsed ? '暂无会话' : '还没有会话'} />
          ) : null}
        </Stack>
      </ScrollArea>

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
