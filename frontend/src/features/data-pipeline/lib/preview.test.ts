import { describe, expect, it } from 'vitest'
import { HttpError } from '../../../shared/api/client'
import type { PipelinePreview } from '../../../shared/types'
import {
  previewRequestError,
  previewState,
  previewTruncationLabel,
} from './preview'

describe('previewState', () => {
  it('marks a preview stale when the draft revision changes', () => {
    expect(previewState({ previewRevision: 3, draftRevision: 4 })).toBe('stale')
    expect(previewState({ previewRevision: 4, draftRevision: 4 })).toBe('ready')
    expect(previewState({ previewRevision: null, draftRevision: 4 })).toBe('idle')
  })
})

describe('previewRequestError', () => {
  it('turns a 409 into a reload-draft instruction', () => {
    const error = new HttpError(409, '{"code":"REVISION_CONFLICT","message":"草稿已变化"}')
    const mapped = previewRequestError(error)

    expect(mapped.kind).toBe('revision')
    expect(mapped.message).toContain('重新读取草稿')
  })

  it('keeps blocking issues from a 422 response', () => {
    const error = new HttpError(422, JSON.stringify({
      code: 'PIPELINE_NOT_READY',
      message: '流程存在阻塞错误',
      details: {
        issues: [{
          code: 'FIELD_NOT_FOUND',
          nodeId: 'node-1',
          field: 'amount',
          message: '上游没有 amount',
          hint: '检查选列配置',
        }],
      },
    }))
    const mapped = previewRequestError(error)

    expect(mapped.kind).toBe('blocked')
    expect(mapped.issues).toHaveLength(1)
    expect(mapped.issues[0]).toMatchObject({
      code: 'FIELD_NOT_FOUND',
      node_id: 'node-1',
      field: 'amount',
    })
  })

  it('accepts the current Java blockingIssues payload', () => {
    const error = new HttpError(422, JSON.stringify({
      code: 'PREVIEW_BLOCKED',
      message: '流程存在阻塞错误',
      details: {
        blockingIssues: [{
          code: 'PORT_TYPE_MISMATCH',
          phase: 'graph',
          blocking: true,
          nodeId: 'node-2',
          portId: 'in',
          message: '端口类型不匹配',
        }],
      },
    }))

    const mapped = previewRequestError(error)

    expect(mapped.kind).toBe('blocked')
    expect(mapped.issues[0]).toMatchObject({
      code: 'PORT_TYPE_MISMATCH',
      node_id: 'node-2',
      port_id: 'in',
    })
  })

  it('turns a single-field Java execution error into one issue', () => {
    const error = new HttpError(422, JSON.stringify({
      code: 'PREVIEW_EXECUTION_FAILED',
      message: '执行失败',
      details: { nodeId: 'node-3', portId: 'out', field: 'amount', message: '金额无法解析' },
    }))

    const mapped = previewRequestError(error)

    expect(mapped.issues).toHaveLength(1)
    expect(mapped.issues[0]).toMatchObject({
      node_id: 'node-3',
      port_id: 'out',
      field: 'amount',
      message: '金额无法解析',
    })
  })
})

describe('previewTruncationLabel', () => {
  it('marks truncated results as non-full results', () => {
    const preview = {
      truncated: true,
      truncation_reason: 'SCAN_ROW_LIMIT',
    } as PipelinePreview

    expect(previewTruncationLabel(preview)).toBe('非全量结果 · 输入扫描达到上限')
    expect(previewTruncationLabel({ ...preview, truncated: false })).toBeNull()
  })
})
