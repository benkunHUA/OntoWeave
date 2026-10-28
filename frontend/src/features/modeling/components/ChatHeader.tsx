import { ActionIcon } from '@mantine/core'
import { CircleCheck, LoaderCircle, Pencil } from 'lucide-react'
import { useState } from 'react'
import { ChatPaneHeader } from '../../../shared/chat/components/ChatPaneHeader'
import { useModeling } from '../context/modelingContext'
import { RenameConversationDialog } from './dialogs/RenameConversationDialog'

/** 对话栏头部：会话标题 + 改名入口 + 运行状态。 */
export function ChatHeader() {
  const { workspace, conversations, agent, toast } = useModeling()
  const [renameOpen, setRenameOpen] = useState(false)
  const [draftTitle, setDraftTitle] = useState('')
  const [isRenaming, setIsRenaming] = useState(false)

  const conversation = workspace.conversation

  function openRenameDialog() {
    if (!conversation) return
    setDraftTitle(conversation.title || '')
    setRenameOpen(true)
  }

  async function submitRename() {
    if (!conversation) return
    const title = draftTitle.trim()
    if (!title) return
    setIsRenaming(true)
    try {
      const updated = await conversations.rename.mutateAsync({ id: conversation.id, title })
      // 图名跟着会话标题走，画布上那句「谁的名字」要立刻对上。
      workspace.applyConversation(updated)
      setRenameOpen(false)
    } catch (reason) {
      toast.report(reason, '会话重命名失败')
    } finally {
      setIsRenaming(false)
    }
  }

  return (
    <>
      <ChatPaneHeader
        eyebrow="Conversation"
        title={conversation?.title || '新对话'}
        status={conversation ? (agent.isRunning ? '正在整理' : '已同步') : '未选择'}
        statusColor={conversation ? (agent.isRunning ? 'blue' : 'teal') : 'gray'}
        statusIcon={conversation
          ? (agent.isRunning ? <LoaderCircle size={14} /> : <CircleCheck size={14} />)
          : undefined}
        titleAdornment={conversation ? (
          <ActionIcon
            type="button"
            variant="subtle"
            color="gray"
            size="sm"
            aria-label="重命名会话"
            onClick={openRenameDialog}
          >
            <Pencil size={14} />
          </ActionIcon>
        ) : null}
      />

      {renameOpen ? (
        <RenameConversationDialog
          title={draftTitle}
          isRenaming={isRenaming}
          onTitleChange={setDraftTitle}
          onCancel={() => setRenameOpen(false)}
          onSubmit={() => void submitRename()}
        />
      ) : null}
    </>
  )
}
