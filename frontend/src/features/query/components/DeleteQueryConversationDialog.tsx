import { Button } from '@mantine/core'
import { Trash2 } from 'lucide-react'
import { DialogShell } from '../../modeling/components/dialogs/DialogShell'
import { QUERY_COPY } from '../lib/constants'

interface DeleteQueryConversationDialogProps {
  title: string
  isDeleting: boolean
  onCancel: () => void
  onConfirm: () => void
}

export function DeleteQueryConversationDialog({
  title,
  isDeleting,
  onCancel,
  onConfirm,
}: DeleteQueryConversationDialogProps) {
  return (
    <DialogShell
      icon={<Trash2 size={20} />}
      iconTone="danger"
      title={QUERY_COPY.deleteTitle}
      subtitle="删除后无法恢复"
      onClose={onCancel}
      footer={
        <>
          <Button type="button" variant="default" onClick={onCancel}>
            {QUERY_COPY.cancel}
          </Button>
          <Button
            type="button"
            color="red"
            loading={isDeleting}
            leftSection={isDeleting ? undefined : <Trash2 size={15} />}
            onClick={onConfirm}
            disabled={isDeleting}
          >
            {isDeleting ? QUERY_COPY.deleting : QUERY_COPY.deleteConfirm}
          </Button>
        </>
      }
    >
      <div className="view-dialog__body">
        <p>
          确定删除「{title}」吗？{QUERY_COPY.deleteBody}
        </p>
      </div>
    </DialogShell>
  )
}
