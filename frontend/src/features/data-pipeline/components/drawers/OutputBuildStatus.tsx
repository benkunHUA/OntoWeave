import { ExternalLink } from 'lucide-react'
import type { PipelineBuild } from '../../../../shared/types'
import { buildStatusLabel, outputElapsedLabel } from '../../lib/buildStatus'
import { BUILD_COPY } from '../../lib/executionConstants'

interface OutputBuildStatusProps {
  build: PipelineBuild | null | undefined
  outputId: string | null
  onOpenBuildDetails?: (buildId: string) => void
}

export function OutputBuildStatus({
  build,
  outputId,
  onOpenBuildDetails,
}: OutputBuildStatusProps) {
  if (!build || !outputId) return null
  const output = build.outputs.find((item) => item.output_id === outputId)
  const logUri = output?.log_uri ?? build.log_uri
  return (
    <section className="pipeline-output-build" aria-label="最近构建">
      <header>
        <strong>最近构建</strong>
        <span>{output ? buildStatusLabel(output.status) : '最近构建未包含此输出'}</span>
      </header>
      {output && outputElapsedLabel(output) ? <small>耗时 {outputElapsedLabel(output)}</small> : null}
      <div>
        {onOpenBuildDetails ? (
          <button type="button" className="text-button" onClick={() => onOpenBuildDetails(build.id)}>
            {BUILD_COPY.viewDetails}
          </button>
        ) : null}
        {logUri ? (
          <a className="pipeline-build-log-link" href={logUri} target="_blank" rel="noreferrer">
            打开构建日志 <ExternalLink size={11} />
          </a>
        ) : <small>{BUILD_COPY.logMissing}</small>}
      </div>
    </section>
  )
}
