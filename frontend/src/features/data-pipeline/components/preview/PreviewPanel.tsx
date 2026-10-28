import { ActionIcon, Alert, Button, Group, Loader, Text, Title } from '@mantine/core'
import { RefreshCw, X } from 'lucide-react'
import type { PipelinePreview, PipelinePreviewIssue } from '../../../../shared/types'
import { useEscapeClose } from '../../../../shared/hooks/useEscapeClose'
import { PREVIEW_COPY } from '../../lib/executionConstants'
import {
  previewElapsedLabel,
  previewRowsLabel,
  previewTruncationLabel,
  type PreviewRequestError,
  type PreviewState,
} from '../../lib/preview'
import { PreviewIssueList } from './PreviewIssueList'
import { PreviewTable } from './PreviewTable'

interface PreviewPanelProps {
  nodeName: string
  draftRevision: number | null
  preview: PipelinePreview | null
  state: PreviewState
  error: PreviewRequestError | null
  isLoading: boolean
  onRefresh: () => void
  onClose: () => void
  onSelectIssue: (issue: PipelinePreviewIssue) => void
}

export function PreviewPanel({
  nodeName,
  draftRevision,
  preview,
  state,
  error,
  isLoading,
  onRefresh,
  onClose,
  onSelectIssue,
}: PreviewPanelProps) {
  useEscapeClose(true, onClose)
  const truncation = preview ? previewTruncationLabel(preview) : null
  const issues = preview ? [...preview.errors, ...preview.warnings] : error?.issues ?? []

  return (
    <section className="pipeline-preview-panel" role="dialog" aria-label={PREVIEW_COPY.title}>
      <header className="pipeline-preview-head">
        <div>
          <span className="eyebrow">Preview</span>
          <Title order={2}>{nodeName}</Title>
          <div className="pipeline-preview-meta">
            <Text component="span" size="xs" c="dimmed">修订 {preview?.draft_revision ?? draftRevision ?? '—'}</Text>
            <Text component="span" size="xs" c="dimmed">{PREVIEW_COPY.currentDraft}</Text>
            {preview ? <Text component="span" size="xs" c="dimmed">{previewRowsLabel(preview)}</Text> : null}
            {preview ? <Text component="span" size="xs" c="dimmed">{previewElapsedLabel(preview.elapsed_ms)}</Text> : null}
            {state === 'stale' ? (
              <Text component="span" className="pipeline-preview-stale" size="xs">{PREVIEW_COPY.stale}</Text>
            ) : null}
          </div>
        </div>
        <div className="pipeline-preview-actions">
          <Button type="button" variant="default" size="xs" leftSection={<RefreshCw size={14} />} onClick={onRefresh} disabled={isLoading}>
            {PREVIEW_COPY.refresh}
          </Button>
          <ActionIcon
            type="button"
            variant="subtle"
            color="gray"
            aria-label={isLoading ? PREVIEW_COPY.cancel : PREVIEW_COPY.close}
            onClick={onClose}
          >
            <X size={16} />
          </ActionIcon>
        </div>
      </header>

      <div className={`pipeline-preview-body${state === 'stale' ? ' is-stale' : ''}`}>
        {isLoading ? (
          <Group className="pipeline-preview-loading" gap="xs"><Loader size="sm" /><Text size="xs">{PREVIEW_COPY.loading}</Text></Group>
        ) : null}
        {error ? (
          <Alert color="red" variant="light" title={error.kind === 'revision' ? '草稿版本冲突' : '预览失败'}>
            <Text size="xs">{error.message}</Text>
          </Alert>
        ) : null}
        {truncation ? <Text className="pipeline-preview-warning" size="xs" c="yellow">{truncation}</Text> : null}
        {preview ? <PreviewTable preview={preview} /> : null}
        {preview?.input_versions.length ? (
          <div className="pipeline-preview-inputs">
            <Text component="strong" size="xs">{PREVIEW_COPY.inputs}</Text>
            {preview.input_versions.map((input) => (
              <Text component="span" size="xs" c="dimmed" key={`${input.dataset_id}-${input.dataset_version_id}`}>
                {input.dataset_id.slice(0, 8)}… · v{input.dataset_version_number}
              </Text>
            ))}
          </div>
        ) : null}
        <PreviewIssueList issues={issues} onSelectIssue={onSelectIssue} />
      </div>
    </section>
  )
}
