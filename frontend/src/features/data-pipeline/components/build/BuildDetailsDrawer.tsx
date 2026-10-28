import { ActionIcon, Alert, Anchor, Button, Group, Loader, Text, Title } from '@mantine/core'
import { Ban, X } from 'lucide-react'
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
          <Title order={2}>{BUILD_COPY.detailsTitle}</Title>
        </div>
        <ActionIcon
          type="button"
          variant="subtle"
          color="gray"
          aria-label="关闭"
          onClick={onClose}
        >
          <X size={16} />
        </ActionIcon>
      </header>

      <div className="pipeline-drawer__body">
        {isLoading && !build ? (
          <Group className="pipeline-preview-loading" gap="xs"><Loader size="sm" /> <Text size="xs">读取构建中…</Text></Group>
        ) : null}
        {error ? <Alert color="red" variant="light"><Text size="xs">{error}</Text></Alert> : null}
        {!build && !isLoading ? <Text className="pipeline-issue-empty" size="xs" c="dimmed">{BUILD_COPY.noBuild}</Text> : null}
        {build ? (
          <>
            <section className="pipeline-build-overview">
              <div>
                <BuildStatusBadge status={build.status} />
                <Text component="span" size="xs">{buildStageLabel(build.stage)}</Text>
                {buildElapsedLabel(build) ? <Text component="span" size="xs" c="dimmed">{buildElapsedLabel(build)}</Text> : null}
              </div>
              <Text component="small" size="xs" c="dimmed">构建 {shortId(build.id)} · {build.trigger}</Text>
              <Text component="small" size="xs" c="dimmed">{formatDateTime(build.started_at)} → {formatDateTime(build.finished_at)}</Text>
              {build.log_uri ? (
                <Anchor
                  className="pipeline-build-log-link"
                  href={build.log_uri}
                  target="_blank"
                  rel="noreferrer"
                  size="xs"
                >
                  {BUILD_COPY.logOpen}
                </Anchor>
              ) : (
                <Text component="small" size="xs" c="dimmed">{BUILD_COPY.logMissing}</Text>
              )}
              {build.error_message || build.error_code ? (
                <Alert color="red" variant="light">
                  <Text size="xs">
                  {build.error_code ? `${build.error_code} · ` : ''}{build.error_message}
                  </Text>
                </Alert>
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
          <Text className="pipeline-save-state" size="xs" c="dimmed" role={cancelError ? 'alert' : undefined}>
            {cancelState === 'sent'
              ? BUILD_COPY.cancelRequested
              : cancelState === 'sending'
                ? BUILD_COPY.cancelSending
                : cancelState === 'failed'
                  ? cancelError ?? BUILD_COPY.cancelFailed
                  : '构建执行中'}
          </Text>
          <Button
            type="button"
            color="red"
            variant="light"
            size="xs"
            leftSection={<Ban size={14} />}
            disabled={cancelState === 'sending' || cancelRequested}
            onClick={() => void requestCancel()}
          >
            {BUILD_COPY.cancelBuild}
          </Button>
        </footer>
      ) : null}
    </aside>
  )
}
