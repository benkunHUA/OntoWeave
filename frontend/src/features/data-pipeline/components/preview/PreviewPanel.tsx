import { LoaderCircle, RefreshCw, X } from 'lucide-react'
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
          <h2>{nodeName}</h2>
          <div className="pipeline-preview-meta">
            <span>修订 {preview?.draft_revision ?? draftRevision ?? '—'}</span>
            <span>{PREVIEW_COPY.currentDraft}</span>
            {preview ? <span>{previewRowsLabel(preview)}</span> : null}
            {preview ? <span>{previewElapsedLabel(preview.elapsed_ms)}</span> : null}
            {state === 'stale' ? (
              <span className="pipeline-preview-stale">{PREVIEW_COPY.stale}</span>
            ) : null}
          </div>
        </div>
        <div className="pipeline-preview-actions">
          <button type="button" className="secondary-button" onClick={onRefresh} disabled={isLoading}>
            <RefreshCw size={14} />
            {PREVIEW_COPY.refresh}
          </button>
          <button
            type="button"
            className="icon-button icon-button--quiet"
            aria-label={isLoading ? PREVIEW_COPY.cancel : PREVIEW_COPY.close}
            onClick={onClose}
          >
            <X size={16} />
          </button>
        </div>
      </header>

      <div className={`pipeline-preview-body${state === 'stale' ? ' is-stale' : ''}`}>
        {isLoading ? (
          <p className="pipeline-preview-loading">
            <LoaderCircle size={16} />
            {PREVIEW_COPY.loading}
          </p>
        ) : null}
        {error ? (
          <div className="pipeline-preview-error">
            <strong>{error.kind === 'revision' ? '草稿版本冲突' : '预览失败'}</strong>
            <p>{error.message}</p>
          </div>
        ) : null}
        {truncation ? <p className="pipeline-preview-warning">{truncation}</p> : null}
        {preview ? <PreviewTable preview={preview} /> : null}
        {preview?.input_versions.length ? (
          <div className="pipeline-preview-inputs">
            <strong>{PREVIEW_COPY.inputs}</strong>
            {preview.input_versions.map((input) => (
              <span key={`${input.dataset_id}-${input.dataset_version_id}`}>
                {input.dataset_id.slice(0, 8)}… · v{input.dataset_version_number}
              </span>
            ))}
          </div>
        ) : null}
        <PreviewIssueList issues={issues} onSelectIssue={onSelectIssue} />
      </div>
    </section>
  )
}
