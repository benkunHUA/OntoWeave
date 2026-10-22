import { Button } from '@mantine/core'
import { ChevronLeft, Pencil } from 'lucide-react'
import { TextField } from '../../../../shared/components/form/controls'
import { DialogShell } from './DialogShell'

interface RenameConversationDialogProps {
  title: string
  isRenaming: boolean
  onTitleChange: (title: string) => void
  onCancel: () => void
  onSubmit: () => void
}

export function RenameConversationDialog({
  title,
  isRenaming,
  onTitleChange,
  onCancel,
  onSubmit,
}: RenameConversationDialogProps) {
  return (
    <DialogShell
      icon={<Pencil size={20} />}
      title="重命名会话"
      subtitle="会话列表和图名称会同步更新"
      onClose={onCancel}
      footer={
        <>
          <Button type="button" variant="default" leftSection={<ChevronLeft size={15} />} onClick={onCancel}>
            取消
          </Button>
          <Button
            type="button"
            loading={isRenaming}
            leftSection={isRenaming ? undefined : <Pencil size={15} />}
            onClick={onSubmit}
            disabled={!title.trim() || isRenaming}
          >
            {isRenaming ? '保存中' : '保存'}
          </Button>
        </>
      }
    >
      <div className="view-dialog__body">
        <TextField
          label="会话名称"
          value={title}
          onChange={(event) => onTitleChange(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Enter') {
              event.preventDefault()
              onSubmit()
            }
          }}
          autoFocus
        />
      </div>
    </DialogShell>
  )
}
