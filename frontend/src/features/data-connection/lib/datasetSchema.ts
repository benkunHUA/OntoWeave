import type { DatasetVersion } from '../../../shared/types'

export interface SchemaField {
  name: string
  type: string
  required: boolean
  doc: string | null
}

function typeText(value: unknown): string {
  if (typeof value === 'string') return value
  if (value && typeof value === 'object') {
    const record = value as Record<string, unknown>
    if (typeof record.type === 'string') return record.type
  }
  return JSON.stringify(value)
}

export function schemaFields(version: DatasetVersion | null): SchemaField[] {
  const schema = version?.schema
  if (!schema || typeof schema !== 'object') return []
  const fields = (schema as { fields?: unknown }).fields
  if (!Array.isArray(fields)) return []
  return fields.flatMap((field) => {
    if (!field || typeof field !== 'object') return []
    const record = field as Record<string, unknown>
    if (typeof record.name !== 'string') return []
    return [{
      name: record.name,
      type: typeText(record.type),
      required: Boolean(record.required),
      doc: typeof record.doc === 'string' ? record.doc : null,
    }]
  })
}
