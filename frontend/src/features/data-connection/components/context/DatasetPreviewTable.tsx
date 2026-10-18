import type { DatasetPreview } from '../../../../shared/types'

interface DatasetPreviewTableProps {
  preview: DatasetPreview | null
  loading: boolean
  error: string | null
}

function cellText(value: unknown): string {
  if (value === null || value === undefined) return '—'
  if (typeof value === 'string') return value
  if (typeof value === 'number' || typeof value === 'boolean') return String(value)
  return JSON.stringify(value)
}

export function DatasetPreviewTable({ preview, loading, error }: DatasetPreviewTableProps) {
  if (loading) return <div className="empty-panel">正在读取数据预览…</div>
  if (error) return <div className="inline-alert is-error">{error}</div>
  if (!preview) return null
  if (preview.rows.length === 0) return <div className="empty-panel">这个版本还没有数据行</div>

  return (
    <>
      <div className="preview-table-wrap">
        <table className="schema-table preview-table">
          <thead>
            <tr>
              {preview.columns.map((column) => <th key={column}>{column}</th>)}
            </tr>
          </thead>
          <tbody>
            {preview.rows.map((row, index) => (
              <tr key={index}>
                {preview.columns.map((column) => (
                  <td key={column} title={cellText(row[column])}>{cellText(row[column])}</td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="preview-note">
        显示前 {preview.rows.length} 行{preview.truncated ? '，还有更多数据' : ''}
      </div>
    </>
  )
}
