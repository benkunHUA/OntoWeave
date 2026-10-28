import { Badge, Group, Paper, Stack, Text } from '@mantine/core'
import type { JsonObject, PipelineBuildOutput } from '../../../../shared/types'
import { expectationSummary } from '../../lib/buildStatus'
import { cellText } from '../../lib/format'

function expectations(output: PipelineBuildOutput): JsonObject[] {
  const raw = output.result?.expectations
  if (!Array.isArray(raw)) return []
  return raw.filter((item): item is JsonObject => Boolean(item) && typeof item === 'object')
}

export function ExpectationResults({ output }: { output: PipelineBuildOutput }) {
  const rows = expectations(output)
  if (!rows.length) return null
  const summary = expectationSummary(output)
  return (
    <Stack component="section" className="pipeline-expectations" gap={6}>
      <Group justify="space-between" gap="xs">
        <Text size="xs" fw={600}>数据期望</Text>
        <Badge color={summary.passed === summary.total ? 'teal' : 'red'} size="xs" variant="light">
          {summary.passed}/{summary.total} 通过
        </Badge>
      </Group>
      {rows.map((item, index) => (
        <Paper
          key={`${cellText(item.type)}-${index}`}
          className={`pipeline-expectation ${item.passed === true ? 'is-ok' : 'is-failed'}`}
          withBorder
          p="xs"
          radius="sm"
        >
          <Group justify="space-between" gap="xs">
            <Text size="xs" fw={600}>{cellText(item.type)}</Text>
            <Badge color={item.passed === true ? 'teal' : 'red'} size="xs" variant="light">
              {item.passed === true ? '通过' : '失败'}
            </Badge>
          </Group>
          <Text size="xs" c="dimmed" mt={4}>
            实际：{cellText(item.actual)} · 期望：{cellText(item.expected)}
          </Text>
          {typeof item.failedRows === 'number' && item.failedRows > 0 ? (
            <Text size="xs" c="dimmed" mt={2}>失败行数：{item.failedRows.toLocaleString()}</Text>
          ) : null}
          {typeof item.message === 'string' ? <Text size="xs" c="dimmed" mt={2}>{item.message}</Text> : null}
        </Paper>
      ))}
    </Stack>
  )
}
