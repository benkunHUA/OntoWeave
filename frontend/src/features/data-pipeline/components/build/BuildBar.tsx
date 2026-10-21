import { CalendarClock, LoaderCircle, Play, TerminalSquare } from 'lucide-react'
import { useState } from 'react'
import type { PipelineBuild } from '../../../../shared/types'
import { DialogShell } from '../../../modeling/components/dialogs/DialogShell'
import { buildElapsedLabel, buildStageLabel } from '../../lib/buildStatus'
import { BUILD_ACTIVE_STATUSES, BUILD_COPY } from '../../lib/executionConstants'
import type { PipelineOutputRef } from '../../lib/outputs'
import { BuildStatusBadge } from './BuildStatusBadge'

interface BuildBarProps {
  revision: number | null
  outputs: PipelineOutputRef[]
  build: PipelineBuild | null
  isCreating: boolean
  error: string | null
  onBuild: (revision: number) => void
  onOpenDetails: (buildId: string) => void
  onOpenSchedule: () => void
}

export function BuildBar({
  revision,
  outputs,
  build,
  isCreating,
  error,
  onBuild,
  onOpenDetails,
  onOpenSchedule,
}: BuildBarProps) {
  const [confirming, setConfirming] = useState(false)
  const active = build ? BUILD_ACTIVE_STATUSES.has(build.status) : false
  const canBuild = revision !== null && outputs.length > 0 && !isCreating && !active

  function confirmBuild() {
    if (revision === null) return
    setConfirming(false)
    onBuild(revision)
  }

  return (
    <div className="pipeline-build-bar">
      {error ? <span className="pipeline-build-bar__error">{error}</span> : null}
      {build ? (
        <button
          type="button"
          className="pipeline-build-status-button"
          aria-label={`${BUILD_COPY.viewDetails}：${buildStageLabel(build.stage)}`}
          onClick={() => onOpenDetails(build.id)}
        >
          <BuildStatusBadge status={build.status} />
          <span>{buildStageLabel(build.stage)}</span>
          {buildElapsedLabel(build) ? <small>{buildElapsedLabel(build)}</small> : null}
          <TerminalSquare size={14} />
        </button>
      ) : null}
      {!active ? (
        <button
          type="button"
          className="primary-button pipeline-build-button"
          disabled={!canBuild}
          title={!outputs.length ? BUILD_COPY.noOutputs : undefined}
          onClick={() => setConfirming(true)}
        >
          {isCreating ? <LoaderCircle size={14} /> : <Play size={14} />}
          {isCreating ? '创建中' : build ? BUILD_COPY.rebuild : BUILD_COPY.build}
        </button>
      ) : null}
      <button type="button" className="secondary-button" onClick={onOpenSchedule}>
        <CalendarClock size={14} />
        {BUILD_COPY.schedule}
      </button>

      {confirming ? (
        <DialogShell
          icon={<Play size={20} />}
          title={BUILD_COPY.confirmTitle}
          subtitle={BUILD_COPY.confirmSubtitle}
          onClose={() => setConfirming(false)}
          footer={<>
            <button type="button" className="secondary-button" onClick={() => setConfirming(false)}>
              {BUILD_COPY.cancel}
            </button>
            <button type="button" className="primary-button" onClick={confirmBuild}>
              <Play size={14} />
              {BUILD_COPY.confirm}
            </button>
          </>}
        >
          <div className="view-dialog__body pipeline-build-confirm">
            <p>{BUILD_COPY.revision} {revision ?? '—'}</p>
            <div>
              <strong>{BUILD_COPY.outputs}</strong>
              <ul>
                {outputs.map((output) => (
                  <li key={output.nodeId}>{output.name}</li>
                ))}
              </ul>
            </div>
            <p className="pipeline-build-frozen-hint">{BUILD_COPY.frozenHint}</p>
          </div>
        </DialogShell>
      ) : null}
    </div>
  )
}
