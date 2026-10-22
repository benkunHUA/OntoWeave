// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '../../../../test/render'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { PipelinePreview } from '../../../../shared/types'
import type { PreviewRequestError } from '../../lib/preview'
import { PreviewPanel } from './PreviewPanel'

const preview: PipelinePreview = {
  preview_id: 'preview-1',
  pipeline_id: 'pipeline-1',
  draft_revision: 4,
  node_id: 'node-a',
  columns: [
    { name: 'id', type: 'long', nullable: false },
    { name: 'customer', type: 'string', nullable: true },
  ],
  rows: [{ id: 1, customer: 'Ada' }],
  returned_rows: 1,
  scanned_rows: 120,
  truncated: true,
  truncation_reason: 'SCAN_ROW_LIMIT',
  elapsed_ms: 1840,
  input_versions: [{
    dataset_kind: 'sync',
    dataset_id: 'dataset-1',
    dataset_version_id: 'version-1',
    dataset_version_number: 7,
    schema_hash: 'hash',
    iceberg_catalog: null,
    iceberg_namespace: null,
    iceberg_table: null,
    iceberg_snapshot_id: null,
    display_snapshot: null,
  }],
  errors: [],
  warnings: [],
}

afterEach(cleanup)

describe('PreviewPanel', () => {
  it('shows the node, revision, scan size and explicit non-full-result warning', () => {
    render(<PreviewPanel
      nodeName="筛选订单"
      draftRevision={4}
      preview={preview}
      state="ready"
      error={null}
      isLoading={false}
      onRefresh={() => undefined}
      onClose={() => undefined}
      onSelectIssue={() => undefined}
    />)

    expect(screen.getByText('筛选订单')).toBeTruthy()
    expect(screen.getByText(/修订 4/)).toBeTruthy()
    expect(screen.getByText(/1 行 · 扫描 120 行/)).toBeTruthy()
    expect(screen.getByText('非全量结果 · 输入扫描达到上限')).toBeTruthy()
    expect(screen.getByText(/1.8 秒/)).toBeTruthy()
    expect(screen.getByText(/v7/)).toBeTruthy()
    expect(screen.getByText('Ada')).toBeTruthy()
  })

  it('opens the owning node field from a structured preview issue', () => {
    const onSelectIssue = vi.fn()
    const error: PreviewRequestError = {
      kind: 'blocked',
      message: '流程存在阻塞错误',
      issues: [{
        code: 'FIELD_NOT_FOUND',
        phase: 'compile',
        level: 'error',
        node_id: 'node-a',
        port_id: 'in',
        field: 'amount',
        row_number: null,
        message: '上游没有 amount',
        hint: '检查选列配置',
        original_value: null,
      }],
    }

    render(<PreviewPanel
      nodeName="筛选订单"
      draftRevision={4}
      preview={null}
      state="error"
      error={error}
      isLoading={false}
      onRefresh={() => undefined}
      onClose={() => undefined}
      onSelectIssue={onSelectIssue}
    />)

    fireEvent.click(screen.getByRole('button', { name: /上游没有 amount/ }))
    expect(onSelectIssue).toHaveBeenCalledWith(error.issues[0])
  })

  it('marks an older preview as stale instead of pretending it is current', () => {
    render(<PreviewPanel
      nodeName="筛选订单"
      draftRevision={5}
      preview={preview}
      state="stale"
      error={null}
      isLoading={false}
      onRefresh={() => undefined}
      onClose={() => undefined}
      onSelectIssue={() => undefined}
    />)

    expect(screen.getByText('草稿已修改，预览已过期')).toBeTruthy()
  })
})
