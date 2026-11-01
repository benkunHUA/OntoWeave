import { Button, Code, Group, Stack, Text } from '@mantine/core'
import { Wrench } from 'lucide-react'
import { DialogShell } from '../../../components/dialogs/DialogShell'
import type { ToolBlock } from '../../lib/executionTrace'
import {
  describeExecutionStep,
  formatExecutionDuration,
} from '../../lib/executionTrace'
import { EXECUTION_TRACE_COPY } from '../../lib/executionTraceCopy'
import { toolLabel } from '../../lib/toolLabels'
import { formatToolArgs, formatToolPayload } from '../../lib/toolPayload'

interface ExecutionStepDetailProps {
  block: ToolBlock
  onClose: () => void
}

export function ExecutionStepDetail({ block, onClose }: ExecutionStepDetailProps) {
  const args = formatToolArgs(block.args)
  const result = formatToolPayload(block.result)
  const duration = formatExecutionDuration(block.durationMs ?? 0)
  const summary = describeExecutionStep(block)

  return (
    <DialogShell
      icon={<Wrench size={18} />}
      title={EXECUTION_TRACE_COPY.detailTitle}
      subtitle={`${summary} · ${toolLabel(block.name, block.args)}`}
      size="lg"
      onClose={onClose}
      footer={(
        <Button type="button" variant="light" onClick={onClose}>
          {EXECUTION_TRACE_COPY.close}
        </Button>
      )}
    >
      <Stack className="execution-step-detail" gap="md">
        <Group gap="xl" className="execution-step-detail__meta">
          <Text size="xs" c="dimmed">
            {EXECUTION_TRACE_COPY.status[block.status]}
          </Text>
          <Text size="xs" c="dimmed">
            {EXECUTION_TRACE_COPY.durationLabel}：{duration || '—'}
          </Text>
          <Text size="xs" c="dimmed" className="execution-step-detail__tool">
            {block.name}
          </Text>
        </Group>
        {args ? (
          <section>
            <Text className="execution-step-detail__caption" size="xs" c="dimmed">
              {EXECUTION_TRACE_COPY.argsTitle}
            </Text>
            <Code block>{args}</Code>
          </section>
        ) : null}
        {result ? (
          <section>
            <Text className="execution-step-detail__caption" size="xs" c="dimmed">
              {EXECUTION_TRACE_COPY.resultTitle}
              {block.truncated ? ` · ${EXECUTION_TRACE_COPY.truncated}` : ''}
            </Text>
            <Code block>{result}</Code>
          </section>
        ) : null}
      </Stack>
    </DialogShell>
  )
}
