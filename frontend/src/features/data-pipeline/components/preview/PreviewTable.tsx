import { ScrollArea, Table, Text } from '@mantine/core'
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
    return <Text size="xs" c="dimmed">{PREVIEW_COPY.noRows}</Text>
  }
  return (
    <ScrollArea className="pipeline-preview-table-wrap" type="auto" offsetScrollbars>
      <Table className="pipeline-preview-table" horizontalSpacing="xs" verticalSpacing="xs">
        <Table.Thead>
          <Table.Tr>
            {preview.columns.map((column) => (
              <Table.Th key={column.name} title={`${column.type}${column.nullable ? ' · nullable' : ''}`}>
                {column.name}
              </Table.Th>
            ))}
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {preview.rows.map((row, index) => (
            <Table.Tr key={`${index}-${rowKey(row, index)}`}>
              {preview.columns.map((column) => (
                <Table.Td key={column.name} title={cellText(row[column.name])}>{cellText(row[column.name])}</Table.Td>
              ))}
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>
    </ScrollArea>
  )
}
