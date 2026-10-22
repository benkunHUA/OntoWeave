import { Button } from '@mantine/core'
import { ChevronLeft, Trash2 } from 'lucide-react'
import { DialogShell } from '../../../modeling/components/dialogs/DialogShell'
import { PIPELINE_DELETE_COPY } from '../../lib/constants'

interface DeletePipelineDialogProps {
  name: string
  isDeleting: boolean
  onCancel: () => void
  onConfirm: () => void
}

export function DeletePipelineDialog({
  name,
  isDeleting,
  onCancel,
  onConfirm,
}: DeletePipelineDialogProps) {
  return (
    <DialogShell
      icon={<Trash2 size={20} />}
      iconTone="danger"
      title={PIPELINE_DELETE_COPY.title}
      subtitle={PIPELINE_DELETE_COPY.subtitle}
      onClose={onCancel}
      footer={
        <>
          <Button type="button" variant="default" leftSection={<ChevronLeft size={15} />} onClick={onCancel} disabled={isDeleting}>
            {PIPELINE_DELETE_COPY.cancel}
          </Button>
          <Button
            type="button"
            color="red"
            loading={isDeleting}
            leftSection={isDeleting ? undefined : <Trash2 size={15} />}
            onClick={onConfirm}
            disabled={isDeleting}
          >
            {isDeleting ? PIPELINE_DELETE_COPY.deleting : PIPELINE_DELETE_COPY.confirm}
          </Button>
        </>
      }
    >
      <div className="view-dialog__body pipeline-delete-dialog">
        <p>{PIPELINE_DELETE_COPY.body}</p>
        <strong>{name}</strong>
      </div>
    </DialogShell>
  )
}
