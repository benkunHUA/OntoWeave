import { Ban } from 'lucide-react'
import { DialogShell } from '../../../modeling/components/dialogs/DialogShell'
import type { RunStatus } from '../../../../shared/types'
import { CANCEL_RUN_DIALOG_COPY, activeRunCancelCopy } from './constants'
import { useConfirmOnce } from './useConfirmOnce'

export interface CancelRunDialogProps {
  runId: string
  status: RunStatus
  isSubmitting?: boolean
  error?: string | null
  onCancel: () => void
  onConfirm: () => void
}

export function CancelRunDialog({
  runId,
  status,
  isSubmitting = false,
  error = null,
  onCancel,
  onConfirm,
}: CancelRunDialogProps) {
  const confirm = useConfirmOnce(isSubmitting, error, onConfirm)

  return (
    <DialogShell
      icon={<Ban size={19} />}
      iconTone="danger"
      title={CANCEL_RUN_DIALOG_COPY.title}
      subtitle={CANCEL_RUN_DIALOG_COPY.subtitle}
      onClose={onCancel}
      footer={
        <>
          <button type="button" className="secondary-button" onClick={onCancel} disabled={isSubmitting}>
            {CANCEL_RUN_DIALOG_COPY.cancel}
          </button>
          <button type="button" className="danger-button" onClick={confirm} disabled={isSubmitting}>
            <Ban size={14} /> {isSubmitting ? CANCEL_RUN_DIALOG_COPY.submitting : CANCEL_RUN_DIALOG_COPY.confirm}
          </button>
        </>
      }
    >
      <div className="view-dialog__body ontology-confirm-dialog">
        <dl className="ontology-confirm-dialog__facts">
          <div><dt>运行 ID</dt><dd><code>{runId}</code></dd></div>
          <div><dt>状态</dt><dd>{status}</dd></div>
        </dl>
        <p className="ontology-confirm-dialog__warning">{activeRunCancelCopy(status)}</p>
        {error ? <p className="ontology-confirm-dialog__error" role="alert">{error}</p> : null}
      </div>
    </DialogShell>
  )
}
