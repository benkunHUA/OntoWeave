import { HttpError } from '../../../shared/api/client'
import { httpErrorInfo } from '../../../shared/api/errors'
import type { PipelinePreview, PipelinePreviewIssue } from '../../../shared/types'

export type PreviewState = 'idle' | 'loading' | 'ready' | 'stale' | 'error'

interface PreviewStateInput {
  previewRevision: number | null
  draftRevision: number | null
  isLoading?: boolean
  hasError?: boolean
}

export function previewState({
  previewRevision,
  draftRevision,
  isLoading = false,
  hasError = false,
}: PreviewStateInput): PreviewState {
  if (hasError) return 'error'
  if (isLoading) return 'loading'
  if (previewRevision === null) return 'idle'
  if (draftRevision !== null && previewRevision !== draftRevision) return 'stale'
  return 'ready'
}

export interface PreviewRequestError {
  kind: 'revision' | 'blocked' | 'unknown'
  message: string
  issues: PipelinePreviewIssue[]
}

function issue(value: Record<string, unknown>): PipelinePreviewIssue {
  return {
    code: typeof value.code === 'string' ? value.code : null,
    phase: typeof value.phase === 'string' ? value.phase : null,
    level: typeof value.level === 'string' ? value.level : null,
    node_id: typeof value.nodeId === 'string'
      ? value.nodeId
      : typeof value.node_id === 'string' ? value.node_id : null,
    port_id: typeof value.portId === 'string'
      ? value.portId
      : typeof value.port_id === 'string' ? value.port_id : null,
    field: typeof value.field === 'string' ? value.field : null,
    row_number: typeof value.rowNumber === 'number'
      ? value.rowNumber
      : typeof value.row_number === 'number' ? value.row_number : null,
    message: typeof value.message === 'string' ? value.message : null,
    hint: typeof value.hint === 'string' ? value.hint : null,
    original_value: typeof value.originalValue === 'string'
      ? value.originalValue
      : typeof value.original_value === 'string' ? value.original_value : null,
  }
}

export function normalizePreviewIssues(value: unknown): PipelinePreviewIssue[] {
  if (!Array.isArray(value)) return []
  return value
    .filter((item): item is Record<string, unknown> => Boolean(item) && typeof item === 'object')
    .map(issue)
}

function issuesFrom(details: Record<string, unknown>, fallbackMessage: string): PipelinePreviewIssue[] {
  const issues = [details.issues, details.blockingIssues]
    .find((value) => Array.isArray(value)) as unknown[] | undefined
  if (issues?.length) {
    return normalizePreviewIssues(issues)
  }
  if (!('nodeId' in details || 'portId' in details || 'field' in details)) return []
  return [issue({
    code: 'PREVIEW_EXECUTION_FAILED',
    phase: 'execution',
    level: 'error',
    nodeId: details.nodeId,
    portId: details.portId,
    field: details.field,
    message: typeof details.message === 'string' ? details.message : fallbackMessage,
  })]
}

export function previewRequestError(reason: unknown): PreviewRequestError {
  if (!(reason instanceof HttpError)) {
    return {
      kind: 'unknown',
      message: reason instanceof Error ? reason.message : '预览失败',
      issues: [],
    }
  }
  const info = httpErrorInfo(reason, '预览失败')
  if (reason.status === 409) {
    return {
      kind: 'revision',
      message: '草稿已更新，请重新读取草稿后再预览',
      issues: [],
    }
  }
  if (reason.status === 422) {
    return {
      kind: 'blocked',
      message: info.message,
      issues: issuesFrom(info.details, info.message),
    }
  }
  return { kind: 'unknown', message: info.message, issues: normalizePreviewIssues(info.issues) }
}

export function previewTruncationLabel(
  preview: Pick<PipelinePreview, 'truncated' | 'truncation_reason'>,
): string | null {
  if (!preview.truncated) return null
  if (preview.truncation_reason === 'SCAN_ROW_LIMIT') {
    return '非全量结果 · 输入扫描达到上限'
  }
  if (preview.truncation_reason === 'OUTPUT_ROW_LIMIT') {
    return '非全量结果 · 返回行数达到上限'
  }
  return preview.truncation_reason
    ? `非全量结果 · ${preview.truncation_reason}`
    : '非全量结果'
}

export function previewRowsLabel(preview: Pick<PipelinePreview, 'returned_rows' | 'scanned_rows'>) {
  const scanned = preview.scanned_rows === null ? '未知' : preview.scanned_rows.toLocaleString()
  return `${preview.returned_rows.toLocaleString()} 行 · 扫描 ${scanned} 行`
}

export function previewElapsedLabel(milliseconds: number): string {
  if (milliseconds < 1000) return `${Math.max(0, Math.round(milliseconds))} 毫秒`
  return `${(milliseconds / 1000).toFixed(milliseconds >= 10_000 ? 0 : 1)} 秒`
}
