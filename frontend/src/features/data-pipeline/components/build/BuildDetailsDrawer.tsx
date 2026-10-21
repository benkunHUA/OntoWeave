import { Ban, LoaderCircle, X } from 'lucide-react'
import { useState } from 'react'
import type { PipelineBuild } from '../../../../shared/types'
import { httpErrorText } from '../../../../shared/api/errors'
import { useEscapeClose } from '../../../../shared/hooks/useEscapeClose'
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
  onCancel: () => Promise<boolean>
  onOpenBuild?: (buildId: string) => void
}

export function BuildDetailsDrawer({
  pipelineId,
  build,
  outputNames,
  isLoading,
  error,
  onClose,
  onCancel,
  onOpenBuild,
}: BuildDetailsDrawerProps) {
  const [cancelState, setCancelState] = useState<'idle' | 'sending' | 'sent' | 'failed'>('idle')
  const [cancelError, setCancelError] = useState<string | null>(null)
  const cancelRequested = cancelState === 'sent'
  useEscapeClose(true, onClose)

  async function requestCancel() {
    if (!build || cancelState === 'sending' || cancelState === 'sent') return
    setCancelState('sending')
    setCancelError(null)
    try {
      const succeeded = await onCancel()
      setCancelState(succeeded ? 'sent' : 'failed')
      if (!succeeded) setCancelError(BUILD_COPY.cancelFailed)
    } catch (reason) {
      setCancelState('failed')
      setCancelError(httpErrorText(reason, BUILD_COPY.cancelFailed))
    }
  }

  return (
    <aside
      className="pipeline-drawer pipeline-build-drawer"
      role="dialog"
      aria-label={BUILD_COPY.detailsTitle}
    >
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
              {build.log_uri ? (
                <a
                  className="pipeline-build-log-link"
                  href={build.log_uri}
                  target="_blank"
                  rel="noreferrer"
                >
                  {BUILD_COPY.logOpen}
                </a>
              ) : (
                <small>{BUILD_COPY.logMissing}</small>
              )}
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
                onOpenBuild={onOpenBuild}
              />
            ))}
          </>
        ) : null}
      </div>

      {build && BUILD_ACTIVE_STATUSES.has(build.status) ? (
        <footer className="pipeline-drawer__foot pipeline-build-drawer__foot">
          <span className="pipeline-save-state" role={cancelError ? 'alert' : undefined}>
            {cancelState === 'sent'
              ? BUILD_COPY.cancelRequested
              : cancelState === 'sending'
                ? BUILD_COPY.cancelSending
                : cancelState === 'failed'
                  ? cancelError ?? BUILD_COPY.cancelFailed
                  : '构建执行中'}
          </span>
          <button
            type="button"
            className="danger-button"
            disabled={cancelState === 'sending' || cancelRequested}
            onClick={() => void requestCancel()}
          >
            <Ban size={14} />
            {BUILD_COPY.cancelBuild}
          </button>
        </footer>
      ) : null}
    </aside>
  )
}
