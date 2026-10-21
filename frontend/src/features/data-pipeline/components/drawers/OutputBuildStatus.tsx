import { ExternalLink } from 'lucide-react'
import type { PipelineBuild } from '../../../../shared/types'
import { buildStatusLabel, outputElapsedLabel } from '../../lib/buildStatus'
import { BUILD_COPY, OUTPUT_BUILD_COPY } from '../../lib/executionConstants'

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
    <section className="pipeline-output-build" aria-label={OUTPUT_BUILD_COPY.ariaLabel}>
      <header>
        <strong>{OUTPUT_BUILD_COPY.title}</strong>
        <span>{output ? buildStatusLabel(output.status) : OUTPUT_BUILD_COPY.missingOutput}</span>
      </header>
      {output && outputElapsedLabel(output)
        ? <small>{OUTPUT_BUILD_COPY.elapsed} {outputElapsedLabel(output)}</small>
        : null}
      <div>
        {onOpenBuildDetails ? (
          <button type="button" className="text-button" onClick={() => onOpenBuildDetails(build.id)}>
            {BUILD_COPY.viewDetails}
          </button>
        ) : null}
        {logUri ? (
          <a className="pipeline-build-log-link" href={logUri} target="_blank" rel="noreferrer">
            {OUTPUT_BUILD_COPY.log} <ExternalLink size={11} />
          </a>
        ) : <small>{BUILD_COPY.logMissing}</small>}
      </div>
    </section>
  )
}
