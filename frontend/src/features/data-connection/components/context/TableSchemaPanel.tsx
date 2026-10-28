import { Alert, Button, Group, Loader, Table, Text, Title } from '@mantine/core'
import { ArrowLeft, KeyRound } from 'lucide-react'
import { useEffect, useState } from 'react'
import { describeTable } from '../../../../shared/api'
import type { TableDetail } from '../../../../shared/types'
import { EmptyState } from '../../../../shared/components/EmptyState'

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
    void (async () => {
      setDetail(null)
      setError(null)
      try {
        const value = await describeTable(connectionId, schema, table)
        if (active) setDetail(value)
      } catch (reason) {
        if (active) setError(reason instanceof Error ? reason.message : '读取表结构失败')
      }
    })()
    return () => {
      active = false
    }
  }, [connectionId, schema, table])

  return (
    <div className="table-schema-panel">
      <Button
        type="button"
        className="back-button"
        variant="subtle"
        size="compact-sm"
        leftSection={<ArrowLeft size={14} />}
        onClick={onBack}
      >
        返回表列表
      </Button>
      <div className="context-section">
        <Group gap={6}>
          <Title order={3}>{schema}.{table}</Title>
        </Group>
        {detail?.comment ? <Text className="context-hint" size="xs" c="dimmed">{detail.comment}</Text> : null}
      </div>
      {error ? <Alert color="red" variant="light" title="读取表结构失败">{error}</Alert> : null}
      {detail ? (
        <div className="schema-table-wrap">
          <Table className="schema-table">
            <Table.Thead>
              <Table.Tr>
                <Table.Th>字段</Table.Th>
                <Table.Th>类型</Table.Th>
                <Table.Th>可空</Table.Th>
                <Table.Th>默认值</Table.Th>
                <Table.Th>主键</Table.Th>
                <Table.Th>注释</Table.Th>
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {detail.columns.map((column) => (
                <Table.Tr key={column.name}>
                  <Table.Td>{column.name}</Table.Td>
                  <Table.Td><code>{column.column_type ?? column.data_type}</code></Table.Td>
                  <Table.Td>{column.nullable ? '是' : '否'}</Table.Td>
                  <Table.Td>{column.default ?? '—'}</Table.Td>
                  <Table.Td>{column.is_primary_key ? <KeyRound size={13} /> : '—'}</Table.Td>
                  <Table.Td>{column.comment ?? '—'}</Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </div>
      ) : !error ? <EmptyState icon={<Loader size="sm" />} title="正在读取字段…" /> : null}
    </div>
  )
}
