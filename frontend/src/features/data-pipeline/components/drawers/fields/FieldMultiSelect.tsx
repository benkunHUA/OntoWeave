import { ActionIcon, Button } from '@mantine/core'
import { Plus, X } from 'lucide-react'
import { useState } from 'react'
import {
  CheckboxField,
  TextField,
} from '../../../../../shared/components/form/controls'
import { EDITOR_COPY } from '../../../lib/editorOptions'

interface FieldMultiSelectProps {
  value: unknown
  options?: string[]
  onChange: (value: string[]) => void
}

function stringsOf(value: unknown): string[] {
  return Array.isArray(value)
    ? value.filter((item): item is string => typeof item === 'string')
    : []
}

function unique(values: string[]): string[] {
  return [...new Set(values.map((value) => value.trim()).filter(Boolean))]
}

export function FieldMultiSelect({ value, options = [], onChange }: FieldMultiSelectProps) {
  const selected = stringsOf(value)
  const available = unique([...options, ...selected])
  const [draft, setDraft] = useState('')

  function toggle(field: string, checked: boolean) {
    onChange(checked ? unique([...selected, field]) : selected.filter((item) => item !== field))
  }

  function add() {
    const field = draft.trim()
    if (!field) return
    onChange(unique([...selected, field]))
    setDraft('')
  }

  return (
    <div className="pipeline-multi-select" data-editor="field-multi-select">
      <div className="pipeline-checkbox-list">
        {available.map((field) => (
          <CheckboxField
            key={field}
            className="pipeline-checkbox"
            checked={selected.includes(field)}
            label={field}
            onChange={(event) => toggle(field, event.currentTarget.checked)}
          />
        ))}
      </div>
      <div className="pipeline-chip-list">
        {selected.map((field) => (
          <span className="pipeline-chip" key={field}>
            {field}
            <ActionIcon
              type="button"
              variant="transparent"
              color="gray"
              size="compact-xs"
              aria-label={`${EDITOR_COPY.remove}${field}`}
              onClick={() => toggle(field, false)}
            >
              <X size={11} />
            </ActionIcon>
          </span>
        ))}
      </div>
      <div className="pipeline-inline-add">
        <TextField
          className="pipeline-inline-control"
          value={draft}
          placeholder={EDITOR_COPY.fieldPlaceholder}
          onChange={(event) => setDraft(event.target.value)}
          onKeyDown={(event) => {
            if (event.key !== 'Enter') return
            event.preventDefault()
            add()
          }}
        />
        <Button
          type="button"
          variant="default"
          size="compact-sm"
          leftSection={<Plus size={13} />}
          onClick={add}
          aria-label={EDITOR_COPY.addField}
        >
          {EDITOR_COPY.addField}
        </Button>
      </div>
    </div>
  )
}
