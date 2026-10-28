import { Button } from '@mantine/core'
import { Play } from 'lucide-react'
import { DialogShell } from '../../../../shared/components/dialogs/DialogShell'
import { GENERATE_DIALOG_COPY } from './constants'
import { useConfirmOnce } from './useConfirmOnce'

export interface GenerateDialogProps {
  resourceName: string
  datasetLabel: string
  datasetVersionLabel?: string | null
  revision: number
  isSubmitting?: boolean
  error?: string | null
  onCancel: () => void
  onConfirm: () => void
}

export function GenerateDialog({
  resourceName,
  datasetLabel,
  datasetVersionLabel,
  revision,
  isSubmitting = false,
  error = null,
  onCancel,
  onConfirm,
}: GenerateDialogProps) {
  const confirm = useConfirmOnce(isSubmitting, error, onConfirm)

  return (
    <DialogShell
      icon={<Play size={19} />}
      title={GENERATE_DIALOG_COPY.title}
      subtitle={GENERATE_DIALOG_COPY.subtitle}
      onClose={onCancel}
      footer={
        <>
          <Button type="button" variant="default" onClick={onCancel} disabled={isSubmitting}>
            {GENERATE_DIALOG_COPY.cancel}
          </Button>
          <Button
            type="button"
            leftSection={<Play size={14} />}
            onClick={confirm}
            disabled={isSubmitting}
          >
            {isSubmitting ? GENERATE_DIALOG_COPY.submitting : GENERATE_DIALOG_COPY.confirm}
          </Button>
        </>
      }
    >
      <div className="view-dialog__body ontology-confirm-dialog">
        <dl className="ontology-confirm-dialog__facts">
          <div><dt>资源</dt><dd>{resourceName}</dd></div>
          <div><dt>{GENERATE_DIALOG_COPY.dataset}</dt><dd>{datasetLabel}</dd></div>
          <div>
            <dt>{GENERATE_DIALOG_COPY.version}</dt>
            <dd>{datasetVersionLabel || GENERATE_DIALOG_COPY.latestVersion}</dd>
          </div>
          <div><dt>{GENERATE_DIALOG_COPY.revision}</dt><dd>{revision}</dd></div>
        </dl>
        <p className="ontology-confirm-dialog__warning">{GENERATE_DIALOG_COPY.warning}</p>
        {error ? <p className="ontology-confirm-dialog__error" role="alert">{error}</p> : null}
      </div>
    </DialogShell>
  )
}
