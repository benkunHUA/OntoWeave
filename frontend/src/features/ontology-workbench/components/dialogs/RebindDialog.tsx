import { Button } from '@mantine/core'
import { RefreshCw } from 'lucide-react'
import { DialogShell } from '../../../../shared/components/dialogs/DialogShell'
import { REBIND_DIALOG_COPY } from './constants'
import { useConfirmOnce } from './useConfirmOnce'

export interface RebindDialogProps {
  resourceName: string
  oldDatasetLabel: string
  newDatasetLabel: string
  isSubmitting?: boolean
  error?: string | null
  onCancel: () => void
  onConfirm: () => void
}

export function RebindDialog({
  resourceName,
  oldDatasetLabel,
  newDatasetLabel,
  isSubmitting = false,
  error = null,
  onCancel,
  onConfirm,
}: RebindDialogProps) {
  const confirm = useConfirmOnce(isSubmitting, error, onConfirm)

  return (
    <DialogShell
      icon={<RefreshCw size={19} />}
      title={REBIND_DIALOG_COPY.title}
      subtitle={REBIND_DIALOG_COPY.subtitle}
      onClose={onCancel}
      footer={
        <>
          <Button type="button" variant="default" onClick={onCancel} disabled={isSubmitting}>
            {REBIND_DIALOG_COPY.cancel}
          </Button>
          <Button
            type="button"
            leftSection={<RefreshCw size={14} />}
            onClick={confirm}
            disabled={isSubmitting}
          >
            {isSubmitting ? REBIND_DIALOG_COPY.submitting : REBIND_DIALOG_COPY.confirm}
          </Button>
        </>
      }
    >
      <div className="view-dialog__body ontology-confirm-dialog">
        <dl className="ontology-confirm-dialog__facts">
          <div><dt>资源</dt><dd>{resourceName}</dd></div>
          <div><dt>{REBIND_DIALOG_COPY.oldDataset}</dt><dd>{oldDatasetLabel}</dd></div>
          <div><dt>{REBIND_DIALOG_COPY.newDataset}</dt><dd>{newDatasetLabel}</dd></div>
        </dl>
        <p className="ontology-confirm-dialog__warning">{REBIND_DIALOG_COPY.warning}</p>
        {error ? <p className="ontology-confirm-dialog__error" role="alert">{error}</p> : null}
      </div>
    </DialogShell>
  )
}
