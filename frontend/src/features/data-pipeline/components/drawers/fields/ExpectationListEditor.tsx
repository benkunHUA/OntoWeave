import { ActionIcon, Button } from '@mantine/core'
import { Plus, X } from 'lucide-react'
import { useState } from 'react'
import {
  NativeSelectField,
  NumberField,
  TextField,
} from '../../../../../shared/components/form/controls'
import type { JsonObject } from '../../../../../shared/types'
import { EDITOR_COPY, EXPECTATION_TYPE_OPTIONS } from '../../../lib/editorOptions'
import { FieldMultiSelect } from './FieldMultiSelect'

interface ExpectationListEditorProps {
  value: unknown
  onChange: (value: JsonObject[]) => void
}

function objectsOf(value: unknown): JsonObject[] {
  return Array.isArray(value)
    ? value.filter((item): item is JsonObject => Boolean(item) && typeof item === 'object')
    : []
}

function newExpectation(type = 'PRIMARY_KEY_UNIQUE'): JsonObject {
  if (type === 'ROW_COUNT_RANGE') return { type, min: 0 }
  if (type === 'VALUE_SET') return { type, column: '', values: [] }
  return { type, columns: [] }
}

function stringArray(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : []
}

function ValuesEditor({ value, onChange }: { value: unknown; onChange: (values: string[]) => void }) {
  const values = stringArray(value)
  const [draft, setDraft] = useState('')

  function add() {
    const next = draft.trim()
    if (!next) return
    onChange([...new Set([...values, next])])
    setDraft('')
  }

  return (
    <div className="pipeline-multi-select">
      <div className="pipeline-chip-list">
        {values.map((item) => (
          <span className="pipeline-chip" key={item}>
            {item}
            <ActionIcon type="button" variant="transparent" color="gray" size="compact-xs" aria-label={`${EDITOR_COPY.remove}${item}`} onClick={() => onChange(values.filter((value) => value !== item))}>
              <X size={11} />
            </ActionIcon>
          </span>
        ))}
      </div>
      <div className="pipeline-inline-add">
        <TextField
          className="pipeline-inline-control"
          value={draft}
          placeholder={EDITOR_COPY.valuePlaceholder}
          onChange={(event) => setDraft(event.target.value)}
          onKeyDown={(event) => {
            if (event.key !== 'Enter') return
            event.preventDefault()
            add()
          }}
        />
        <Button type="button" variant="default" size="compact-sm" leftSection={<Plus size={13} />} onClick={add}>
          {EDITOR_COPY.addValue}
        </Button>
      </div>
    </div>
  )
}

function ExpectationRow({ expectation, onChange, onRemove }: {
  expectation: JsonObject
  onChange: (value: JsonObject) => void
  onRemove: () => void
}) {
  const type = typeof expectation.type === 'string' ? expectation.type : 'PRIMARY_KEY_UNIQUE'
  return (
    <div className="pipeline-expectation-row">
      <div className="pipeline-ast-row">
        <NativeSelectField
          className="pipeline-ast-control"
          aria-label="期望类型"
          value={type}
          data={EXPECTATION_TYPE_OPTIONS}
          onChange={(event) => onChange(newExpectation(event.target.value))}
        />
        <ActionIcon type="button" variant="subtle" color="red" aria-label={EDITOR_COPY.remove} onClick={onRemove}>
          <X size={13} />
        </ActionIcon>
      </div>
      {type === 'PRIMARY_KEY_UNIQUE' || type === 'NOT_NULL' ? (
        <div className="pipeline-subfield">
          <span>{EDITOR_COPY.columns}</span>
          <FieldMultiSelect
            value={expectation.columns}
            onChange={(columns) => onChange({ ...expectation, columns })}
          />
        </div>
      ) : null}
      {type === 'ROW_COUNT_RANGE' ? (
        <div className="pipeline-ast-row">

          <NumberField
            className="pipeline-ast-control"
            min={0}
            aria-label={EDITOR_COPY.minRows}
            placeholder={EDITOR_COPY.minRows}
            value={typeof expectation.min === 'number' ? expectation.min : ''}
            onChange={(next) => onChange({
              ...expectation,
              min: next === '' ? undefined : Number(next),
            })}
          />
          <NumberField
            className="pipeline-ast-control"
            min={0}
            aria-label={EDITOR_COPY.maxRows}
            placeholder={EDITOR_COPY.maxRows}
            value={typeof expectation.max === 'number' ? expectation.max : ''}
            onChange={(next) => onChange({
              ...expectation,
              max: next === '' ? undefined : Number(next),
            })}
          />
        </div>
      ) : null}
      {type === 'VALUE_SET' ? (
        <div className="pipeline-subfield">
          <span>{EDITOR_COPY.column}</span>
          <TextField
            className="pipeline-ast-control"
            value={typeof expectation.column === 'string' ? expectation.column : ''}
            placeholder={EDITOR_COPY.fieldPlaceholder}
            onChange={(event) => onChange({ ...expectation, column: event.target.value })}
          />
          <span>{EDITOR_COPY.values}</span>
          <ValuesEditor value={expectation.values} onChange={(values) => onChange({ ...expectation, values })} />
        </div>
      ) : null}
    </div>
  )
}

export function ExpectationListEditor({ value, onChange }: ExpectationListEditorProps) {
  const expectations = objectsOf(value)
  return (
    <div className="pipeline-expectation-list" data-editor="expectation-list">
      {expectations.map((expectation, index) => (
        <ExpectationRow
          key={index}
          expectation={expectation}
          onChange={(next) => onChange(expectations.map((item, itemIndex) => itemIndex === index ? next : item))}
          onRemove={() => onChange(expectations.filter((_, itemIndex) => itemIndex !== index))}
        />
      ))}
      {!expectations.length ? <p className="pipeline-field-hint">可选：为输出增加主键、行数、取值或非空检查。</p> : null}
      <Button
        type="button"
        variant="default"
        size="compact-sm"
        leftSection={<Plus size={13} />}
        onClick={() => onChange([...expectations, newExpectation()])}
      >
        {EDITOR_COPY.addExpectation}
      </Button>
    </div>
  )
}
