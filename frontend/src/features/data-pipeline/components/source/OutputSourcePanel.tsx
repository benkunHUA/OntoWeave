import { Alert, Anchor, Button, Group, Loader, Text, Title } from '@mantine/core'
import { Database, GitBranch } from 'lucide-react'
import { buildStatusLabel } from '../../lib/buildStatus'
import { SOURCE_COPY } from '../../lib/executionConstants'
import { formatCount, formatDateTime } from '../../lib/format'
import { shortId } from '../../lib/outputs'
import { sourceInputView } from '../../lib/source'
import { useOutputSources } from '../../hooks/useOutputSources'

interface OutputSourcePanelProps {
  pipelineId: string
  datasetId: string
  datasetVersionId: string | null
  onOpenBuild?: (buildId: string) => void
}

export function OutputSourcePanel({
  pipelineId,
  datasetId,
  datasetVersionId,
  onOpenBuild,
}: OutputSourcePanelProps) {
  const { source, isLoading, error } = useOutputSources({
    pipelineId,
    datasetId,
    datasetVersionId,
  })

  if (isLoading) {
    return <Group className="pipeline-source-loading" gap="xs"><Loader size="sm" /><Text size="xs">{SOURCE_COPY.loading}</Text></Group>
  }
  if (error) return <Alert color="red" variant="light"><Text size="xs">{error}</Text></Alert>
  if (!source) return null

  return (
    <section className="pipeline-source-panel" aria-label={SOURCE_COPY.title}>
      <header>
        <span className="eyebrow">{SOURCE_COPY.title}</span>
        <Title order={4}>{source.output.name}</Title>
      </header>
      <div className="pipeline-source-summary">
        <Text component="span" size="xs" c="dimmed">{SOURCE_COPY.output} v{source.output.version_number}</Text>
        <Text component="span" size="xs" c="dimmed">{formatCount(source.output.row_count)} 行</Text>
        <Text component="span" size="xs" c="dimmed">{formatDateTime(source.output.published_at)}</Text>
      </div>
      <div className="pipeline-source-grid">
        {source.build ? (
          <div>
            <Text component="span" size="xs" c="dimmed">{SOURCE_COPY.build}</Text>
            <Text component="strong" size="sm">{shortId(source.build.build_id)} · {buildStatusLabel(source.build.status)}</Text>
            <Text component="small" size="xs" c="dimmed">{formatDateTime(source.build.finished_at ?? source.build.started_at)}</Text>
            <div className="pipeline-source-build-actions">
              {onOpenBuild ? (
                <Button type="button" variant="subtle" size="compact-xs" onClick={() => onOpenBuild(source.build!.build_id)}>
                  {SOURCE_COPY.buildDetails}
                </Button>
              ) : null}
              {source.build.log_uri ? (
                <Anchor className="pipeline-build-log-link" href={source.build.log_uri} target="_blank" rel="noreferrer" size="xs">
                  {SOURCE_COPY.buildLog}
                </Anchor>
              ) : <Text component="small" size="xs" c="dimmed">{SOURCE_COPY.logMissing}</Text>}
            </div>
          </div>
        ) : <Text className="pipeline-source-note" size="xs" c="dimmed">{SOURCE_COPY.buildUnavailable}</Text>}
        {source.pipeline_version ? (
          <div>
            <Text component="span" size="xs" c="dimmed">{SOURCE_COPY.pipelineVersion}</Text>
            <Text component="strong" size="sm"><GitBranch size={12} /> v{source.pipeline_version.version_number}</Text>
            <Text component="small" size="xs" c="dimmed">{formatDateTime(source.pipeline_version.created_at)}</Text>
          </div>
        ) : null}
      </div>

      <div className="pipeline-source-inputs">
        <Text component="strong" size="xs">{SOURCE_COPY.directInputs}</Text>
        {source.inputs.length ? source.inputs.map((input) => {
          const view = sourceInputView(input)
          return (
            <article key={`${input.dataset_id}-${input.dataset_version_id ?? 'none'}`}>
              <div className="pipeline-source-input-title">
                <Database size={13} />
                <span>{view.title}</span>
                <em>{view.versionLabel}</em>
              </div>
              {view.connection ? <Text size="xs" c="dimmed">连接：{view.connection}</Text> : null}
              {view.sourceTable ? <Text size="xs" c="dimmed">源表：{view.sourceTable}</Text> : null}
              {view.note ? <Text className="pipeline-source-note" size="xs" c="dimmed">{view.note}</Text> : null}
            </article>
          )
        }) : <Text className="pipeline-source-note" size="xs" c="dimmed">{SOURCE_COPY.noInputs}</Text>}
      </div>
    </section>
  )
}
