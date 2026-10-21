// @vitest-environment jsdom

import { act, cleanup, renderHook, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { HttpError } from '../../../shared/api/client'
import type { PipelineDraftResponse } from '../../../shared/types'
import { usePipelineCommandQueue } from './usePipelineCommandQueue'

const api = vi.hoisted(() => ({
  applyPipelineDraftCommand: vi.fn(),
  movePipelineNodes: vi.fn(),
  undoPipelineTurn: vi.fn(),
}))

vi.mock('../../../shared/api', () => api)

function draft(pipelineId: string, revision: number): PipelineDraftResponse {
  return {
    pipeline_id: pipelineId,
    revision,
    definition: { schemaVersion: 1, nodes: [], edges: [] },
  }
}

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((resolvePromise) => {
    resolve = resolvePromise
  })
  return { promise, resolve }
}

function renderQueue(pipelineId: string, current: PipelineDraftResponse, setDraft = vi.fn()) {
  return renderHook(
    ({ id, draftValue }: { id: string; draftValue: PipelineDraftResponse | null }) =>
      usePipelineCommandQueue({
        pipelineId: id,
        draft: draftValue,
        setDraft,
        refreshCheck: vi.fn().mockResolvedValue(null),
        reload: vi.fn().mockResolvedValue(null),
      }),
    { initialProps: { id: pipelineId, draftValue: current } },
  )
}

describe('usePipelineCommandQueue pipeline generations', () => {
  beforeEach(() => {
    api.applyPipelineDraftCommand.mockReset()
    api.movePipelineNodes.mockReset()
    api.undoPipelineTurn.mockReset()
  })

  afterEach(() => cleanup())

  it('resets a halted queue so pipeline B can write after A conflicts', async () => {
    api.applyPipelineDraftCommand
      .mockRejectedValueOnce(new HttpError(409, '{"code":"REVISION_CONFLICT"}'))
      .mockResolvedValueOnce(draft('pipeline-b', 8))

    const setDraft = vi.fn()
    const { result, rerender } = renderQueue('pipeline-a', draft('pipeline-a', 3), setDraft)

    await act(async () => {
      await result.current.updateNode('node-1', 'A', {})
    })
    expect(result.current.hasConflict).toBe(true)

    rerender({ id: 'pipeline-b', draftValue: draft('pipeline-b', 7) })
    await waitFor(() => expect(result.current.hasConflict).toBe(false))

    await act(async () => {
      await result.current.updateNode('node-2', 'B', {})
    })

    expect(api.applyPipelineDraftCommand).toHaveBeenCalledTimes(2)
    expect(api.applyPipelineDraftCommand.mock.calls[1][0]).toBe('pipeline-b')
    expect(setDraft).toHaveBeenLastCalledWith(draft('pipeline-b', 8))
    expect(result.current.hasConflict).toBe(false)
  })

  it('reads base revision inside the serialized tail for consecutive writes', async () => {
    const first = deferred<PipelineDraftResponse>()
    const second = deferred<PipelineDraftResponse>()
    api.applyPipelineDraftCommand
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise)

    const { result } = renderQueue('pipeline-a', draft('pipeline-a', 5))

    let firstWrite!: Promise<unknown>
    let secondWrite!: Promise<unknown>
    act(() => {
      firstWrite = result.current.updateNode('node-1', 'First', {})
      secondWrite = result.current.updateNode('node-2', 'Second', {})
    })

    await waitFor(() => expect(api.applyPipelineDraftCommand).toHaveBeenCalledTimes(1))
    expect(api.applyPipelineDraftCommand.mock.calls[0][1].base_revision).toBe(5)

    act(() => first.resolve(draft('pipeline-a', 6)))
    await waitFor(() => expect(api.applyPipelineDraftCommand).toHaveBeenCalledTimes(2))
    expect(api.applyPipelineDraftCommand.mock.calls[1][1].base_revision).toBe(6)

    act(() => second.resolve(draft('pipeline-a', 7)))
    await act(async () => {
      await firstWrite
      await secondWrite
    })
    expect(result.current.hasConflict).toBe(false)
  })

  it('starts pipeline B commands without waiting for a deferred A command', async () => {
    const pendingA = deferred<PipelineDraftResponse>()
    api.applyPipelineDraftCommand
      .mockReturnValueOnce(pendingA.promise)
      .mockResolvedValueOnce(draft('pipeline-b', 8))

    const setDraft = vi.fn()
    const { result, rerender } = renderQueue('pipeline-a', draft('pipeline-a', 3), setDraft)

    let first!: Promise<unknown>
    act(() => {
      first = result.current.updateNode('node-1', 'A', {})
    })
    await waitFor(() => expect(api.applyPipelineDraftCommand).toHaveBeenCalledTimes(1))

    rerender({ id: 'pipeline-b', draftValue: draft('pipeline-b', 7) })
    await act(async () => {
      await result.current.updateNode('node-2', 'B', {})
    })

    expect(api.applyPipelineDraftCommand).toHaveBeenCalledTimes(2)
    expect(setDraft).toHaveBeenLastCalledWith(draft('pipeline-b', 8))

    act(() => pendingA.resolve(draft('pipeline-a', 4)))
    await act(async () => {
      await first
    })

    expect(setDraft).toHaveBeenLastCalledWith(draft('pipeline-b', 8))
  })
})
