import { Anchor, Button, Code, Group, Paper, Stack, Text } from '@mantine/core'
import type { PipelineBuildOutput } from '../../../../shared/types'
import { useState } from 'react'
import {
  buildStageLabel,
  outputElapsedLabel,
  outputRowCount,
} from '../../lib/buildStatus'
import { BUILD_COPY } from '../../lib/executionConstants'
import { formatCount } from '../../lib/format'
import { shortId } from '../../lib/outputs'
import { OutputDatasetDialog } from '../source/OutputDatasetDialog'
import { BuildStatusBadge } from './BuildStatusBadge'
import { ExpectationResults } from './ExpectationResults'

interface BuildOutputCardProps {
  pipelineId: string
  output: PipelineBuildOutput
  name: string | undefined
  onOpenBuild?: (buildId: string) => void
}

export function BuildOutputCard({ pipelineId, output, name, onOpenBuild }: BuildOutputCardProps) {
  const [datasetOpen, setDatasetOpen] = useState(false)
  const published = Boolean(output.dataset_id && output.dataset_version_id)
  return (
    <Paper component="article" className="pipeline-build-output" withBorder p="sm" radius="md">
      <Group component="header" justify="space-between" align="flex-start" gap="xs">
        <Stack gap={2}>
          <Text component="strong" size="sm">{name ?? `输出 ${shortId(output.output_id)}`}</Text>
          <Text component="small" size="xs" c="dimmed">{BUILD_COPY.outputTitle} · {shortId(output.output_id)}</Text>
        </Stack>
        <BuildStatusBadge status={output.status} />
      </Group>
      <Group className="pipeline-build-output-meta" gap="xs">
        <Text component="span" size="xs" c="dimmed">{buildStageLabel(output.stage)}</Text>
        <Text component="span" size="xs" c="dimmed">{formatCount(outputRowCount(output))} 行</Text>
        {outputElapsedLabel(output) ? <Text component="span" size="xs" c="dimmed">{outputElapsedLabel(output)}</Text> : null}
      </Group>
      <ExpectationResults output={output} />
      <Stack className="pipeline-build-diagnostics" gap={4}>
        <Text component="strong" size="xs">{BUILD_COPY.diagnostics}</Text>
        {output.log_uri ? (
          <Anchor className="pipeline-build-log-link" href={output.log_uri} target="_blank" rel="noreferrer" size="xs">
            {BUILD_COPY.logOpen}
          </Anchor>
        ) : <Text component="small" size="xs" c="dimmed">{BUILD_COPY.logMissing}</Text>}
        {output.error_message || output.error_code ? (
          <Text size="xs" c="red">{output.error_code ? `${output.error_code} · ` : ''}{output.error_message}</Text>
        ) : <Text size="xs" c="dimmed">{BUILD_COPY.noDiagnostics}</Text>}
        {output.engine_job_id ? <Text component="small" size="xs" c="dimmed">引擎作业：{output.engine_job_id}</Text> : null}
        {output.stats ? <Code block>{JSON.stringify(output.stats, null, 2)}</Code> : null}
      </Stack>
      {published ? (
        <Button type="button" variant="default" size="xs" onClick={() => setDatasetOpen(true)}>
          {BUILD_COPY.outputDataset}
        </Button>
      ) : null}
      {datasetOpen && output.dataset_id && output.dataset_version_id ? (
        <OutputDatasetDialog
          pipelineId={pipelineId}
          output={output}
          name={name ?? `输出 ${shortId(output.output_id)}`}
          onClose={() => setDatasetOpen(false)}
          onOpenBuild={onOpenBuild}
        />
      ) : null}
    </Paper>
  )
}
