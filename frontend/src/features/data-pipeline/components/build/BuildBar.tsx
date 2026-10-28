import { Button, List, Stack, Text, UnstyledButton } from '@mantine/core'
import { CalendarClock, LoaderCircle, Play, TerminalSquare } from 'lucide-react'
import { useState } from 'react'
import type { PipelineBuild, PipelineCheckResult } from '../../../../shared/types'
import { DialogShell } from '../../../../shared/components/dialogs/DialogShell'
import { buildElapsedLabel, buildStageLabel } from '../../lib/buildStatus'
import { BUILD_ACTIVE_STATUSES, BUILD_COPY } from '../../lib/executionConstants'
import type { PipelineOutputRef } from '../../lib/outputs'
import { BuildStatusBadge } from './BuildStatusBadge'

interface BuildBarProps {
  revision: number | null
  check: PipelineCheckResult | null
  outputs: PipelineOutputRef[]
  isWriting: boolean
  build: PipelineBuild | null
  isCreating: boolean
  error: string | null
  onPrepareBuild: () => Promise<number | null>
  onBuild: (revision: number) => void
  onOpenDetails: (buildId: string) => void
  onOpenSchedule: () => void
}

export function BuildBar({
  revision,
  check,
  outputs,
  isWriting,
  build,
  isCreating,
  error,
  onPrepareBuild,
  onBuild,
  onOpenDetails,
  onOpenSchedule,
}: BuildBarProps) {
  const [confirming, setConfirming] = useState(false)
  const [preparing, setPreparing] = useState(false)
  const [prepareError, setPrepareError] = useState<string | null>(null)
  const [confirmRevision, setConfirmRevision] = useState<number | null>(null)
  const active = build ? BUILD_ACTIVE_STATUSES.has(build.status) : false
  const unbound = outputs.some((output) => !output.outputId)
  const blocking = !check || check.blocking_issues.length > 0
  const canBuild = revision !== null && outputs.length > 0 && !unbound && !blocking &&
    !isWriting && !isCreating && !preparing && !active
  const blockTitle = !outputs.length
    ? BUILD_COPY.noOutputs
    : unbound
      ? BUILD_COPY.noBoundOutputs
      : blocking
        ? BUILD_COPY.blockingIssues
        : isWriting
          ? BUILD_COPY.savingDraft
          : undefined

  async function prepareBuild() {
    setPrepareError(null)
    setPreparing(true)
    try {
      const prepared = await onPrepareBuild()
      if (prepared === null) {
        setPrepareError(BUILD_COPY.prepareFailed)
        return
      }
      setConfirmRevision(prepared)
      setConfirming(true)
    } catch {
      setPrepareError(BUILD_COPY.prepareFailed)
    } finally {
      setPreparing(false)
    }
  }

  function confirmBuild() {
    if (confirmRevision === null) return
    setConfirming(false)
    onBuild(confirmRevision)
  }

  return (
    <div className="pipeline-build-bar">
      {error || prepareError ? (
        <Text className="pipeline-build-bar__error" size="xs" c="red">{error ?? prepareError}</Text>
      ) : null}
      {build ? (
        <UnstyledButton
          type="button"
          className="pipeline-build-status-button"
          aria-label={`${BUILD_COPY.viewDetails}：${buildStageLabel(build.stage)}`}
          onClick={() => onOpenDetails(build.id)}
        >
          <BuildStatusBadge status={build.status} />
          <span>{buildStageLabel(build.stage)}</span>
          {buildElapsedLabel(build) ? <small>{buildElapsedLabel(build)}</small> : null}
          <TerminalSquare size={14} />
        </UnstyledButton>
      ) : null}
      {!active ? (
        <Button
          type="button"
          className="pipeline-build-button"
          disabled={!canBuild}
          title={blockTitle}
          leftSection={isCreating || preparing ? <LoaderCircle size={14} /> : <Play size={14} />}
          onClick={() => void prepareBuild()}
        >
          {isCreating ? BUILD_COPY.creating : preparing ? BUILD_COPY.preparing : build ? BUILD_COPY.rebuild : BUILD_COPY.build}
        </Button>
      ) : null}
      <Button
        type="button"
        variant="default"
        leftSection={<CalendarClock size={14} />}
        onClick={onOpenSchedule}
      >
        {BUILD_COPY.schedule}
      </Button>

      {confirming ? (
        <DialogShell
          icon={<Play size={20} />}
          title={BUILD_COPY.confirmTitle}
          subtitle={BUILD_COPY.confirmSubtitle}
          onClose={() => setConfirming(false)}
          footer={<>
            <Button type="button" variant="default" onClick={() => setConfirming(false)}>
              {BUILD_COPY.cancel}
            </Button>
            <Button type="button" leftSection={<Play size={14} />} onClick={confirmBuild}>
              {BUILD_COPY.confirm}
            </Button>
          </>}
        >
          <Stack className="view-dialog__body pipeline-build-confirm" gap="sm">
            <Text size="sm">{BUILD_COPY.revision} {confirmRevision ?? '—'}</Text>
            <Stack gap={4}>
              <Text component="strong" size="sm">{BUILD_COPY.outputs}</Text>
              <List size="sm">
                {outputs.map((output) => (
                  <List.Item key={output.nodeId}>{output.name}</List.Item>
                ))}
              </List>
            </Stack>
            <Text className="pipeline-build-frozen-hint" size="xs">{BUILD_COPY.frozenHint}</Text>
          </Stack>
        </DialogShell>
      ) : null}
    </div>
  )
}
