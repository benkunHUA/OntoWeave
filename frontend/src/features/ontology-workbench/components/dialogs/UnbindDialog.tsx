import { Button, Checkbox } from '@mantine/core'
import { Unlink } from 'lucide-react'
import { useState } from 'react'
import { DialogShell } from '../../../../shared/components/dialogs/DialogShell'
import { UNBIND_DIALOG_COPY } from './constants'
import { useConfirmOnce } from './useConfirmOnce'

export interface UnbindDialogProps {
  resourceName: string
  objectCount: number
  relationCount: number
  isSubmitting?: boolean
  error?: string | null
  onCancel: () => void
  onConfirm: () => void
}

export function UnbindDialog({
  resourceName,
  objectCount,
  relationCount,
  isSubmitting = false,
  error = null,
  onCancel,
  onConfirm,
}: UnbindDialogProps) {
  const [acknowledged, setAcknowledged] = useState(false)
  const confirm = useConfirmOnce(isSubmitting, error, onConfirm)

  return (
    <DialogShell
      icon={<Unlink size={19} />}
      iconTone="danger"
      title={UNBIND_DIALOG_COPY.title}
      subtitle={UNBIND_DIALOG_COPY.subtitle}
      onClose={onCancel}
      footer={
        <>
          <Button type="button" variant="default" onClick={onCancel} disabled={isSubmitting}>
            {UNBIND_DIALOG_COPY.cancel}
          </Button>
          <Button
            type="button"
            color="red"
            onClick={confirm}
            disabled={isSubmitting || !acknowledged}
            leftSection={<Unlink size={14} />}
          >
            {isSubmitting ? UNBIND_DIALOG_COPY.submitting : UNBIND_DIALOG_COPY.confirm}
          </Button>
        </>
      }
    >
      <div className="view-dialog__body ontology-confirm-dialog">
        <strong className="ontology-confirm-dialog__subject">{resourceName}</strong>
        <dl className="ontology-confirm-dialog__facts">
          <div>
            <dt>{UNBIND_DIALOG_COPY.objectCount}</dt>
            <dd>{objectCount} {UNBIND_DIALOG_COPY.unit}</dd>
          </div>
          <div>
            <dt>{UNBIND_DIALOG_COPY.relationCount}</dt>
            <dd>{relationCount} {UNBIND_DIALOG_COPY.unit}</dd>
          </div>
        </dl>
        <p className="ontology-confirm-dialog__warning">{UNBIND_DIALOG_COPY.warning}</p>
        <Checkbox
          className="ontology-confirm-dialog__ack"
          checked={acknowledged}
          onChange={(event) => setAcknowledged(event.currentTarget.checked)}
          disabled={isSubmitting}
          label={UNBIND_DIALOG_COPY.acknowledgement}
        />
        {error ? <p className="ontology-confirm-dialog__error" role="alert">{error}</p> : null}
      </div>
    </DialogShell>
  )
}
