import type { PipelinePreview, JsonObject } from '../../../../shared/types'
import { PREVIEW_COPY } from '../../lib/executionConstants'
import { cellText } from '../../lib/format'

interface PreviewTableProps {
  preview: PipelinePreview
}

function rowKey(row: JsonObject, index: number): string {
  try {
    return JSON.stringify(row)
  } catch {
    return String(index)
  }
}

export function PreviewTable({ preview }: PreviewTableProps) {
  if (!preview.rows.length) {
    return <p className="pipeline-preview-empty">{PREVIEW_COPY.noRows}</p>
  }
  return (
    <div className="pipeline-preview-table-wrap">
      <table className="pipeline-preview-table">
        <thead>
          <tr>
            {preview.columns.map((column) => (
              <th key={column.name} title={`${column.type}${column.nullable ? ' · nullable' : ''}`}>
                {column.name}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {preview.rows.map((row, index) => (
            <tr key={`${index}-${rowKey(row, index)}`}>
              {preview.columns.map((column) => (
                <td key={column.name}>{cellText(row[column.name])}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
