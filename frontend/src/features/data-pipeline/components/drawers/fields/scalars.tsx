import type { ConfigSchema } from '../../../../../shared/types'

interface ScalarProps {
  value: unknown
  schema: ConfigSchema
  onChange: (value: unknown) => void
}

export function TextControl({ value, onChange }: ScalarProps) {
  return (
    <input
      type="text"
      value={typeof value === 'string' ? value : ''}
      onChange={(event) => onChange(event.target.value)}
    />
  )
}

export function NumberControl({ value, onChange }: ScalarProps) {
  return (
    <input
      type="number"
      value={typeof value === 'number' ? value : ''}
      onChange={(event) => {
        const next = event.target.value
        onChange(next === '' ? undefined : Number(next))
      }}
    />
  )
}

export function BooleanControl({ value, onChange }: ScalarProps) {
  return (
    <label className="pipeline-checkbox">
      <input
        type="checkbox"
        checked={value === true}
        onChange={(event) => onChange(event.target.checked)}
      />
      <span>{value === true ? '开启' : '关闭'}</span>
    </label>
  )
}

export function SelectControl({ value, schema, onChange }: ScalarProps) {
  const options = Array.isArray(schema.enum)
    ? schema.enum.filter((item): item is string => typeof item === 'string')
    : []
  if (!options.length) {
    return <TextControl value={value} schema={schema} onChange={onChange} />
  }
  return (
    <select
      value={typeof value === 'string' ? value : ''}
      onChange={(event) => onChange(event.target.value)}
    >
      <option value="">请选择</option>
      {options.map((option) => (
        <option key={option} value={option}>
          {option}
        </option>
      ))}
    </select>
  )
}
