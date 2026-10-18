import { ArrowLeft, KeyRound } from 'lucide-react'
import { useEffect, useState } from 'react'
import { describeTable } from '../../../../shared/api'
import type { TableDetail } from '../../../../shared/types'

interface TableSchemaPanelProps {
  connectionId: string
  schema: string
  table: string
  onBack: () => void
}

export function TableSchemaPanel({ connectionId, schema, table, onBack }: TableSchemaPanelProps) {
  const [detail, setDetail] = useState<TableDetail | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    setDetail(null)
    setError(null)
    void describeTable(connectionId, schema, table)
      .then((value) => {
        if (active) setDetail(value)
      })
      .catch((reason: unknown) => {
        if (active) setError(reason instanceof Error ? reason.message : '读取表结构失败')
      })
    return () => {
      active = false
    }
  }, [connectionId, schema, table])

  return (
    <div className="table-schema-panel">
      <button type="button" className="back-button" onClick={onBack}>
        <ArrowLeft size={14} />
        返回表列表
      </button>
      <div className="context-section">
        <h2>{schema}.{table}</h2>
        {detail?.comment ? <p className="context-hint">{detail.comment}</p> : null}
      </div>
      {error ? <div className="inline-alert is-error">{error}</div> : null}
      {detail ? (
        <div className="schema-table-wrap">
          <table className="schema-table">
            <thead>
              <tr>
                <th>字段</th>
                <th>类型</th>
                <th>可空</th>
                <th>默认值</th>
                <th>主键</th>
                <th>注释</th>
              </tr>
            </thead>
            <tbody>
              {detail.columns.map((column) => (
                <tr key={column.name}>
                  <td>{column.name}</td>
                  <td><code>{column.column_type ?? column.data_type}</code></td>
                  <td>{column.nullable ? '是' : '否'}</td>
                  <td>{column.default ?? '—'}</td>
                  <td>{column.is_primary_key ? <KeyRound size={13} /> : '—'}</td>
                  <td>{column.comment ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : !error ? <div className="empty-panel">正在读取字段…</div> : null}
    </div>
  )
}
