import {
  NativeSelectField,
  NumberField,
  TextField,
} from '../../../../../shared/components/form/controls'
import type { LiteralOperand } from '../../../lib/conditionAst'
import { DATA_TYPE_OPTIONS, EDITOR_COPY } from '../../../lib/editorOptions'

interface LiteralValueEditorProps {
  value: LiteralOperand
  onChange: (value: LiteralOperand) => void
}

function convertedValue(dataType: string, value: unknown): unknown {
  if (dataType === 'boolean') return value === true
  if (dataType === 'long' || dataType === 'double') {
    const number = typeof value === 'number' ? value : Number(value)
    return Number.isFinite(number) ? number : 0
  }
  return value === undefined || value === null ? '' : String(value)
}

export function LiteralValueEditor({ value, onChange }: LiteralValueEditorProps) {
  function changeType(dataType: string) {
    onChange({ ...value, dataType, value: convertedValue(dataType, value.value) })
  }

  return (
    <span className="pipeline-literal-editor" data-editor="literal-editor">
      <NativeSelectField
        className="pipeline-literal-control"
        aria-label={EDITOR_COPY.dataType}
        value={value.dataType}
        data={DATA_TYPE_OPTIONS.map((type) => ({ value: type, label: type }))}
        onChange={(event) => changeType(event.target.value)}
      />
      {value.dataType === 'boolean' ? (
        <NativeSelectField
          className="pipeline-literal-control"
          aria-label={EDITOR_COPY.value}
          value={value.value === true ? 'true' : 'false'}
          data={[
            { value: 'true', label: 'true' },
            { value: 'false', label: 'false' },
          ]}
          onChange={(event) => onChange({ ...value, value: event.target.value === 'true' })}
        />
      ) : (
        value.dataType === 'long' || value.dataType === 'double' ? (
          <NumberField
            className="pipeline-literal-control"
            aria-label={EDITOR_COPY.value}
            value={typeof value.value === 'number' ? value.value : ''}
            placeholder={EDITOR_COPY.value}
            onChange={(next) => onChange({
              ...value,
              value: convertedValue(value.dataType, next),
            })}
          />
        ) : (
          <TextField
            className="pipeline-literal-control"
          aria-label={EDITOR_COPY.value}
          value={typeof value.value === 'string' || typeof value.value === 'number' ? value.value : ''}
          placeholder={EDITOR_COPY.value}
          onChange={(event) => onChange({
            ...value,
            value: convertedValue(value.dataType, event.target.value),
          })}
          />
        )
      )}
    </span>
  )
}
