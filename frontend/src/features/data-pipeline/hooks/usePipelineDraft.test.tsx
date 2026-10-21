// @vitest-environment jsdom

import { act, cleanup, renderHook, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type {
  NodeCatalog,
  PipelineCheckResult,
  PipelineDraftResponse,
} from '../../../shared/types'
import { usePipelineDraft } from './usePipelineDraft'

const api = vi.hoisted(() => ({
  checkPipeline: vi.fn(),
  getNodeCatalog: vi.fn(),
  getPipelineDraft: vi.fn(),
}))

vi.mock('../../../shared/api', () => api)

const catalog: NodeCatalog = { catalog_version: 'v1', nodes: [] }

function draft(pipelineId: string, revision: number): PipelineDraftResponse {
  return {
    pipeline_id: pipelineId,
    revision,
    definition: { schemaVersion: 1, nodes: [], edges: [] },
  }
}

function check(pipelineId: string, revision: number): PipelineCheckResult {
  return {
    pipeline_id: pipelineId,
    revision,
    stage: 'draft',
    blocking_issues: [],
    warnings: [],
    next_actions: [],
  }
}

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

describe('usePipelineDraft pipeline generations', () => {
  beforeEach(() => {
    api.getNodeCatalog.mockResolvedValue(catalog)
    api.checkPipeline.mockImplementation((pipelineId: string, revision: number) =>
      Promise.resolve(check(pipelineId, revision)))
    api.getPipelineDraft.mockReset()
  })

  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
  })

  it('does not let a deferred A response overwrite pipeline B', async () => {
    const draftA = deferred<PipelineDraftResponse>()
    const draftB = deferred<PipelineDraftResponse>()
    api.getPipelineDraft.mockImplementation((pipelineId: string) =>
      pipelineId === 'pipeline-a' ? draftA.promise : draftB.promise)

    const { result, rerender } = renderHook(
      ({ pipelineId }: { pipelineId: string }) => usePipelineDraft(pipelineId),
      { initialProps: { pipelineId: 'pipeline-a' } },
    )

    await waitFor(() => expect(api.getPipelineDraft).toHaveBeenCalledWith('pipeline-a'))

    rerender({ pipelineId: 'pipeline-b' })

    await waitFor(() => expect(result.current.draft).toBeNull())
    await waitFor(() => expect(api.getPipelineDraft).toHaveBeenCalledWith('pipeline-b'))

    act(() => draftA.resolve(draft('pipeline-a', 3)))
    await act(async () => {
      await Promise.resolve()
    })
    expect(result.current.draft).toBeNull()

    act(() => draftB.resolve(draft('pipeline-b', 7)))
    await waitFor(() => expect(result.current.draft?.pipeline_id).toBe('pipeline-b'))
    expect(result.current.draft?.revision).toBe(7)
  })
})
