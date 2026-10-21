import { Database, GitBranch, LoaderCircle } from 'lucide-react'
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
}

export function OutputSourcePanel({
  pipelineId,
  datasetId,
  datasetVersionId,
}: OutputSourcePanelProps) {
  const { source, isLoading, error } = useOutputSources({
    pipelineId,
    datasetId,
    datasetVersionId,
  })

  if (isLoading) {
    return <p className="pipeline-source-loading"><LoaderCircle size={14} />{SOURCE_COPY.loading}</p>
  }
  if (error) return <p className="pipeline-alert is-error">{error}</p>
  if (!source) return null

  return (
    <section className="pipeline-source-panel" aria-label={SOURCE_COPY.title}>
      <header>
        <span className="eyebrow">{SOURCE_COPY.title}</span>
        <h4>{source.output.name}</h4>
      </header>
      <div className="pipeline-source-summary">
        <span>{SOURCE_COPY.output} v{source.output.version_number}</span>
        <span>{formatCount(source.output.row_count)} 行</span>
        <span>{formatDateTime(source.output.published_at)}</span>
      </div>
      <div className="pipeline-source-grid">
        {source.build ? (
          <div>
            <span>{SOURCE_COPY.build}</span>
            <strong>{shortId(source.build.build_id)} · {buildStatusLabel(source.build.status)}</strong>
            <small>{formatDateTime(source.build.finished_at ?? source.build.started_at)}</small>
          </div>
        ) : null}
        {source.pipeline_version ? (
          <div>
            <span>{SOURCE_COPY.pipelineVersion}</span>
            <strong><GitBranch size={12} /> v{source.pipeline_version.version_number}</strong>
            <small>{formatDateTime(source.pipeline_version.created_at)}</small>
          </div>
        ) : null}
      </div>

      <div className="pipeline-source-inputs">
        <strong>{SOURCE_COPY.directInputs}</strong>
        {source.inputs.length ? source.inputs.map((input) => {
          const view = sourceInputView(input)
          return (
            <article key={`${input.dataset_id}-${input.dataset_version_id ?? 'none'}`}>
              <div className="pipeline-source-input-title">
                <Database size={13} />
                <span>{view.title}</span>
                <em>{view.versionLabel}</em>
              </div>
              {view.connection ? <p>连接：{view.connection}</p> : null}
              {view.sourceTable ? <p>源表：{view.sourceTable}</p> : null}
              {view.note ? <p className="pipeline-source-note">{view.note}</p> : null}
            </article>
          )
        }) : <p className="pipeline-source-note">{SOURCE_COPY.noInputs}</p>}
      </div>
    </section>
  )
}
