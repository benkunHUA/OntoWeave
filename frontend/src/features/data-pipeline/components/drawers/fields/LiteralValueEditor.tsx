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
      <select
        aria-label={EDITOR_COPY.dataType}
        value={value.dataType}
        onChange={(event) => changeType(event.target.value)}
      >
        {DATA_TYPE_OPTIONS.map((type) => <option key={type} value={type}>{type}</option>)}
      </select>
      {value.dataType === 'boolean' ? (
        <select
          aria-label={EDITOR_COPY.value}
          value={value.value === true ? 'true' : 'false'}
          onChange={(event) => onChange({ ...value, value: event.target.value === 'true' })}
        >
          <option value="true">true</option>
          <option value="false">false</option>
        </select>
      ) : (
        <input
          type={value.dataType === 'long' || value.dataType === 'double' ? 'number' : 'text'}
          aria-label={EDITOR_COPY.value}
          value={typeof value.value === 'string' || typeof value.value === 'number' ? value.value : ''}
          placeholder={EDITOR_COPY.value}
          onChange={(event) => onChange({
            ...value,
            value: convertedValue(value.dataType, event.target.value),
          })}
        />
      )}
    </span>
  )
}
