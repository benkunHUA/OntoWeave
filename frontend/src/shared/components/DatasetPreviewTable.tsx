import { Alert, Loader, Table, Text } from '@mantine/core'
import type { DatasetPreview } from '../types'
import { EmptyState } from './EmptyState'

interface DatasetPreviewTableProps {
  preview: DatasetPreview | null
  loading: boolean
  error: string | null
  limit: number
}

function cellText(value: unknown): string {
  if (value === null || value === undefined) return '—'
  if (typeof value === 'string') return value
  if (typeof value === 'number' || typeof value === 'boolean') return String(value)
  return JSON.stringify(value)
}

export function DatasetPreviewTable({
  preview,
  loading,
  error,
  limit,
}: DatasetPreviewTableProps) {
  if (loading) {
    return <EmptyState icon={<Loader size="sm" />} title="正在读取数据预览…" />
  }
  if (error) {
    return <Alert color="red" variant="light" title="读取数据预览失败">{error}</Alert>
  }
  if (!preview) return null
  if (preview.rows.length === 0) {
    return (
      <EmptyState
        title={preview.live ? '还没有可预览的数据行' : '这个版本还没有数据行'}
      />
    )
  }
  const rows = preview.rows.slice(0, limit)
  const truncated = preview.truncated || preview.rows.length > rows.length
  const columnNames = preview.columns.map((column) => column.name)

  return (
    <>
      <div className="preview-table-wrap">
        <Table className="schema-table preview-table">
          <Table.Thead>
            <Table.Tr>
              {preview.columns.map((column) => (
                <Table.Th key={column.name} title={column.type}>{column.name}</Table.Th>
              ))}
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {rows.map((row, index) => (
              <Table.Tr key={index}>
                {columnNames.map((column) => (
                  <Table.Td key={column} title={cellText(row[column])}>{cellText(row[column])}</Table.Td>
                ))}
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      </div>
      <Text className={preview.live ? 'preview-note is-live' : 'preview-note'} size="xs" c="dimmed">
        {preview.live
          ? `实时预览 · 未发布 · 前 ${rows.length} 行 · snapshot ${preview.snapshot_id}`
          : `显示前 ${rows.length} 行${truncated ? '，还有更多数据' : ''}`}
      </Text>
    </>
  )
}
