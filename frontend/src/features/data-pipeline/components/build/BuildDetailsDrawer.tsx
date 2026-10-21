import { Ban, LoaderCircle, X } from 'lucide-react'
import { useState } from 'react'
import type { PipelineBuild } from '../../../../shared/types'
import { buildElapsedLabel, buildStageLabel } from '../../lib/buildStatus'
import { BUILD_ACTIVE_STATUSES, BUILD_COPY } from '../../lib/executionConstants'
import { formatDateTime } from '../../lib/format'
import { shortId } from '../../lib/outputs'
import { BuildOutputCard } from './BuildOutputCard'
import { BuildStatusBadge } from './BuildStatusBadge'

interface BuildDetailsDrawerProps {
  pipelineId: string
  build: PipelineBuild | null
  outputNames: Record<string, string>
  isLoading: boolean
  error: string | null
  onClose: () => void
  onCancel: () => void
}

export function BuildDetailsDrawer({
  pipelineId,
  build,
  outputNames,
  isLoading,
  error,
  onClose,
  onCancel,
}: BuildDetailsDrawerProps) {
  const [cancelBuildId, setCancelBuildId] = useState<string | null>(null)
  const cancelRequested = Boolean(build && cancelBuildId === build.id)

  function requestCancel() {
    if (build) setCancelBuildId(build.id)
    onCancel()
  }

  return (
    <aside className="pipeline-drawer pipeline-build-drawer" aria-label={BUILD_COPY.detailsTitle}>
      <header className="pipeline-drawer__head">
        <div>
          <span className="eyebrow">Build</span>
          <h2>{BUILD_COPY.detailsTitle}</h2>
        </div>
        <button
          type="button"
          className="icon-button icon-button--quiet"
          aria-label="关闭"
          onClick={onClose}
        >
          <X size={16} />
        </button>
      </header>

      <div className="pipeline-drawer__body">
        {isLoading && !build ? (
          <p className="pipeline-preview-loading"><LoaderCircle size={16} />读取构建中…</p>
        ) : null}
        {error ? <p className="pipeline-alert is-error">{error}</p> : null}
        {!build && !isLoading ? <p className="pipeline-issue-empty">{BUILD_COPY.noBuild}</p> : null}
        {build ? (
          <>
            <section className="pipeline-build-overview">
              <div>
                <BuildStatusBadge status={build.status} />
                <span>{buildStageLabel(build.stage)}</span>
                {buildElapsedLabel(build) ? <span>{buildElapsedLabel(build)}</span> : null}
              </div>
              <small>构建 {shortId(build.id)} · {build.trigger}</small>
              <small>{formatDateTime(build.started_at)} → {formatDateTime(build.finished_at)}</small>
              {build.error_message || build.error_code ? (
                <p className="pipeline-alert is-error">
                  {build.error_code ? `${build.error_code} · ` : ''}{build.error_message}
                </p>
              ) : null}
            </section>
            {build.outputs.map((output) => (
              <BuildOutputCard
                key={output.id}
                pipelineId={pipelineId}
                output={output}
                name={outputNames[output.output_id]}
              />
            ))}
          </>
        ) : null}
      </div>

      {build && BUILD_ACTIVE_STATUSES.has(build.status) ? (
        <footer className="pipeline-drawer__foot pipeline-build-drawer__foot">
          <span className="pipeline-save-state">
            {cancelRequested ? BUILD_COPY.cancelRequested : '构建执行中'}
          </span>
          <button
            type="button"
            className="danger-button"
            disabled={cancelRequested}
            onClick={requestCancel}
          >
            <Ban size={14} />
            {BUILD_COPY.cancelBuild}
          </button>
        </footer>
      ) : null}
    </aside>
  )
}
