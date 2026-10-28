import { Button } from '@mantine/core'
import { ChevronLeft, Pencil } from 'lucide-react'
import { TextField } from '../../../../shared/components/form/controls'
import { DialogShell } from '../../../../shared/components/dialogs/DialogShell'
import { PIPELINE_RENAME_COPY } from '../../lib/constants'

interface RenamePipelineDialogProps {
  name: string
  isSaving: boolean
  onNameChange: (name: string) => void
  onCancel: () => void
  onSubmit: () => void
}

export function RenamePipelineDialog({
  name,
  isSaving,
  onNameChange,
  onCancel,
  onSubmit,
}: RenamePipelineDialogProps) {
  return (
    <DialogShell
      icon={<Pencil size={20} />}
      title={PIPELINE_RENAME_COPY.title}
      subtitle={PIPELINE_RENAME_COPY.subtitle}
      onClose={onCancel}
      footer={
        <>
          <Button type="button" variant="default" leftSection={<ChevronLeft size={15} />} onClick={onCancel}>
            {PIPELINE_RENAME_COPY.cancel}
          </Button>
          <Button
            type="button"
            loading={isSaving}
            leftSection={isSaving ? undefined : <Pencil size={15} />}
            onClick={onSubmit}
            disabled={!name.trim() || isSaving}
          >
            {isSaving ? PIPELINE_RENAME_COPY.saving : PIPELINE_RENAME_COPY.save}
          </Button>
        </>
      }
    >
      <div className="view-dialog__body">
        <TextField
          label={PIPELINE_RENAME_COPY.label}
          value={name}
          maxLength={200}
          onChange={(event) => onNameChange(event.target.value)}
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
