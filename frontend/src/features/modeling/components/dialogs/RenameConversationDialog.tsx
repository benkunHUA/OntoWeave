import { ChevronLeft, LoaderCircle, Pencil } from 'lucide-react'
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
          <button type="button" className="secondary-button" onClick={onCancel}>
            <ChevronLeft size={15} />
            取消
          </button>
          <button
            type="button"
            className="primary-button"
            onClick={onSubmit}
            disabled={!title.trim() || isRenaming}
          >
            {isRenaming ? <LoaderCircle size={15} /> : <Pencil size={15} />}
            {isRenaming ? '保存中' : '保存'}
          </button>
        </>
      }
    >
      <label>
        会话名称
        <input
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
      </label>
    </DialogShell>
  )
}
