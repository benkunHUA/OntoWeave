import { Button } from '@mantine/core'
import { useState } from 'react'
import {
  NativeSelectField,
  TextField,
} from '../../../../../shared/components/form/controls'
import type { JsonObject } from '../../../../../shared/types'
import { EDITOR_COPY } from '../../../lib/editorOptions'
import type { OutputDraftInput } from '../../../lib/outputCommand'
import { ExpectationListEditor } from './ExpectationListEditor'
import { FieldMultiSelect } from './FieldMultiSelect'

interface OutputSelectProps {
  nodeId: string
  defaultName: string
  outputId: string | null
  isReadOnly: boolean
  isSaving: boolean
  onSetOutput: (nodeId: string, output: OutputDraftInput) => Promise<unknown> | null
}

export function OutputSelect({
  nodeId,
  defaultName,
  outputId,
  isReadOnly,
  isSaving,
  onSetOutput,
}: OutputSelectProps) {
  const [name, setName] = useState(defaultName)
  const [primaryKeys, setPrimaryKeys] = useState<string[]>([])
  const [expectations, setExpectations] = useState<JsonObject[]>([])
  const [submitting, setSubmitting] = useState(false)

  if (outputId) {
    return (
      <section className="pipeline-output-select" data-editor="output-select">
        <h3>{EDITOR_COPY.outputName}</h3>
        <p className="pipeline-readonly">{outputId}</p>
        <p className="pipeline-field-hint">{EDITOR_COPY.outputBound}</p>
      </section>
    )
  }

  async function submit() {
    if (!name.trim() || submitting || isReadOnly) return
    setSubmitting(true)
    try {
      await onSetOutput(nodeId, {
        name,
        writeMode: 'snapshot',
        primaryKeyColumns: primaryKeys,
        expectations,
      })
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="pipeline-output-select" data-editor="output-select">
      <h3>{EDITOR_COPY.outputName}</h3>
      <div className="pipeline-subfield">
        <TextField
          label={EDITOR_COPY.outputName}
          value={name}
          disabled={isReadOnly}
          onChange={(event) => setName(event.target.value)}
        />
      </div>
      <div className="pipeline-subfield">
        <NativeSelectField
          label={EDITOR_COPY.writeMode}
          value="snapshot"
          data={[{ value: 'snapshot', label: EDITOR_COPY.snapshot }]}
          disabled
        />
      </div>
      <div className="pipeline-subfield">
        <span>{EDITOR_COPY.primaryKey}</span>
        <FieldMultiSelect value={primaryKeys} onChange={setPrimaryKeys} />
      </div>
      <div className="pipeline-subfield">
        <span>{EDITOR_COPY.expectations}</span>
        <ExpectationListEditor value={expectations} onChange={setExpectations} />
      </div>
      <Button
        type="button"
        disabled={isReadOnly || submitting || isSaving || !name.trim()}
        loading={submitting || isSaving}
        onClick={() => void submit()}
      >
        {submitting || isSaving ? EDITOR_COPY.saving : EDITOR_COPY.saveOutput}
      </Button>
      {!name.trim() ? <p className="pipeline-field-error">{EDITOR_COPY.outputInvalid}</p> : null}
    </section>
  )
}
