import type { PipelineBuildOutput } from '../../../../shared/types'
import {
  buildStageLabel,
  outputElapsedLabel,
  outputRowCount,
} from '../../lib/buildStatus'
import { BUILD_COPY } from '../../lib/executionConstants'
import { formatCount } from '../../lib/format'
import { shortId } from '../../lib/outputs'
import { OutputSourcePanel } from '../source/OutputSourcePanel'
import { BuildStatusBadge } from './BuildStatusBadge'
import { ExpectationResults } from './ExpectationResults'

interface BuildOutputCardProps {
  pipelineId: string
  output: PipelineBuildOutput
  name: string | undefined
}

export function BuildOutputCard({ pipelineId, output, name }: BuildOutputCardProps) {
  return (
    <article className="pipeline-build-output">
      <header>
        <div>
          <strong>{name ?? `输出 ${shortId(output.output_id)}`}</strong>
          <small>{BUILD_COPY.outputTitle} · {shortId(output.output_id)}</small>
        </div>
        <BuildStatusBadge status={output.status} />
      </header>
      <div className="pipeline-build-output-meta">
        <span>{buildStageLabel(output.stage)}</span>
        <span>{formatCount(outputRowCount(output))} 行</span>
        {outputElapsedLabel(output) ? <span>{outputElapsedLabel(output)}</span> : null}
      </div>
      <ExpectationResults output={output} />
      <section className="pipeline-build-diagnostics">
        <strong>{BUILD_COPY.diagnostics}</strong>
        {output.error_message || output.error_code ? (
          <p>{output.error_code ? `${output.error_code} · ` : ''}{output.error_message}</p>
        ) : <p>{BUILD_COPY.noDiagnostics}</p>}
        {output.engine_job_id ? <small>引擎作业：{output.engine_job_id}</small> : null}
        {output.stats ? <pre>{JSON.stringify(output.stats, null, 2)}</pre> : null}
      </section>
      {output.dataset_id && output.dataset_version_id ? (
        <OutputSourcePanel
          pipelineId={pipelineId}
          datasetId={output.dataset_id}
          datasetVersionId={output.dataset_version_id}
        />
      ) : null}
    </article>
  )
}
