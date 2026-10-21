import { Plus, Trash2 } from 'lucide-react'

export interface ArrayRowColumn {
  key: string
  label: string
  options?: string[]
}

interface ArrayRowsControlProps {
  value: unknown
  columns: ArrayRowColumn[]
  emptyRow: Record<string, string>
  addLabel: string
  onChange: (value: Array<Record<string, string>>) => void
}

function rowsOf(value: unknown, columns: ArrayRowColumn[]): Array<Record<string, string>> {
  if (!Array.isArray(value)) return []
  return value.map((row) => {
    const source = row && typeof row === 'object' ? (row as Record<string, unknown>) : {}
    const normalized: Record<string, string> = {}
    for (const column of columns) {
      normalized[column.key] = typeof source[column.key] === 'string'
        ? String(source[column.key])
        : ''
    }
    return normalized
  })
}

export function ArrayRowsControl({
  value,
  columns,
  emptyRow,
  addLabel,
  onChange,
}: ArrayRowsControlProps) {
  const rows = rowsOf(value, columns)

  function update(index: number, key: string, next: string) {
    onChange(rows.map((row, rowIndex) =>
      rowIndex === index ? { ...row, [key]: next } : row,
    ))
  }

  return (
    <div className="pipeline-array-rows">
      {rows.map((row, index) => (
        <div className="pipeline-array-row" key={index}>
          {columns.map((column) => (
            column.options?.length ? (
              <select
                key={column.key}
                aria-label={column.label}
                value={row[column.key]}
                onChange={(event) => update(index, column.key, event.target.value)}
              >
                <option value="">{column.label}</option>
                {column.options.map((option) => (
                  <option key={option} value={option}>{option}</option>
                ))}
              </select>
            ) : (
              <input
                key={column.key}
                aria-label={column.label}
                placeholder={column.label}
                value={row[column.key]}
                onChange={(event) => update(index, column.key, event.target.value)}
              />
            )
          ))}
          <button
            type="button"
            className="icon-button icon-button--quiet"
            aria-label={`删除${addLabel}`}
            onClick={() => onChange(rows.filter((_, rowIndex) => rowIndex !== index))}
          >
            <Trash2 size={14} />
          </button>
        </div>
      ))}
      <button
        type="button"
        className="pipeline-add-row"
        onClick={() => onChange([...rows, { ...emptyRow }])}
      >
        <Plus size={14} />
        {addLabel}
      </button>
    </div>
  )
}
