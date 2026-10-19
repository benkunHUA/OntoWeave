import type { DatasetPreview } from '../../../../shared/types'
import { DATASET_PREVIEW_LIMIT } from '../../lib/constants'

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
  if (preview.rows.length === 0) {
    return <div className="empty-panel">{preview.live ? '还没有可预览的数据行' : '这个版本还没有数据行'}</div>
  }
  const rows = preview.rows.slice(0, DATASET_PREVIEW_LIMIT)
  const truncated = preview.truncated || preview.rows.length > rows.length

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
            {rows.map((row, index) => (
              <tr key={index}>
                {preview.columns.map((column) => (
                  <td key={column} title={cellText(row[column])}>{cellText(row[column])}</td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className={preview.live ? 'preview-note is-live' : 'preview-note'}>
        {preview.live
          ? `实时预览 · 未发布 · 前 ${rows.length} 行 · snapshot ${preview.snapshot_id}`
          : `显示前 ${rows.length} 行${truncated ? '，还有更多数据' : ''}`}
      </div>
    </>
  )
}
