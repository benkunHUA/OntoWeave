import {
  CheckboxField,
  NativeSelectField,
  NumberField,
  TextField,
} from '../../../../../shared/components/form/controls'
import type { ConfigSchema } from '../../../../../shared/types'

interface ScalarProps {
  value: unknown
  schema: ConfigSchema
  onChange: (value: unknown) => void
}

export function TextControl({ value, onChange }: ScalarProps) {
  return (
    <TextField
      value={typeof value === 'string' ? value : ''}
      onChange={(event) => onChange(event.target.value)}
    />
  )
}

export function NumberControl({ value, onChange }: ScalarProps) {
  return (
    <NumberField
      value={typeof value === 'number' ? value : ''}
      onChange={(next) => {
        onChange(next === '' ? undefined : Number(next))
      }}
    />
  )
}

export function BooleanControl({ value, onChange }: ScalarProps) {
  return (
    <CheckboxField
      className="pipeline-checkbox"
      checked={value === true}
      label={value === true ? '开启' : '关闭'}
      onChange={(event) => onChange(event.currentTarget.checked)}
    />
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
    <NativeSelectField
      value={typeof value === 'string' ? value : ''}
      data={[
        { value: '', label: '请选择' },
        ...options.map((option) => ({ value: option, label: option })),
      ]}
      onChange={(event) => onChange(event.target.value)}
    />
  )
}
