// @vitest-environment jsdom

import { act, cleanup, renderHook, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { HttpError } from '../../../shared/api/client'
import type {
  PipelineBuild,
  PipelineDraftResponse,
  PipelinePreview,
  PipelineSchedule,
  PipelineSource,
} from '../../../shared/types'
import { usePipelineExecutionUi } from './usePipelineExecutionUi'
import { usePipelineBuild } from './usePipelineBuild'
import { useOutputSources } from './useOutputSources'
import { usePipelinePreview } from './usePipelinePreview'
import { usePipelineSchedule } from './usePipelineSchedule'

const api = vi.hoisted(() => ({
  cancelPipelineBuild: vi.fn(),
  cancelPipelinePreview: vi.fn(),
  createPipelineBuild: vi.fn(),
  getPipelineBuild: vi.fn(),
  getPipelineSchedule: vi.fn(),
  getPipelineOutputSources: vi.fn(),
  listPipelineScheduleRuns: vi.fn(),
  pausePipelineSchedule: vi.fn(),
  previewPipelineNode: vi.fn(),
  putPipelineSchedule: vi.fn(),
  resumePipelineSchedule: vi.fn(),
}))
vi.mock('../../../shared/api', () => api)

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((resolvePromise) => {
    resolve = resolvePromise
  })
  return { promise, resolve }
}

function preview(pipelineId: string, nodeId: string): PipelinePreview {
  return {
    preview_id: 'preview-1',
    pipeline_id: pipelineId,
    draft_revision: 3,
    node_id: nodeId,
    columns: [],
    rows: [],
    returned_rows: 0,
    scanned_rows: 0,
    truncated: false,
    truncation_reason: null,
    elapsed_ms: 1,
    input_versions: [],
    errors: [],
    warnings: [],
  }
}

function source(datasetId: string): PipelineSource {
  return {
    pipeline_id: 'pipeline-a',
    dataset_id: datasetId,
    version_id: 'version-a',
    output: { output_id: 'output-1', name: datasetId, version_number: 1, row_count: 0, published_at: null },
    build: null,
    pipeline_version: null,
    inputs: [],
  }
}

function build(pipelineId: string): PipelineBuild {
  return {
    id: `build-${pipelineId}`,
    pipeline_id: pipelineId,
    pipeline_version_id: null,
    status: 'succeeded',
    stage: 'finished',
    trigger: 'manual',
    error_code: null,
    error_message: null,
    log_uri: null,
    started_at: null,
    finished_at: null,
    outputs: [],
  }
}

function schedule(pipelineId: string): PipelineSchedule {
  return {
    id: `schedule-${pipelineId}`,
    pipeline_id: pipelineId,
    name: pipelineId,
    status: 'active',
    trigger_type: 'schedule',
    cron_expression: '0 9 * * *',
    timezone: 'Asia/Shanghai',
    next_run_at: null,
    last_run_at: null,
    paused_at: null,
  }
}

beforeEach(() => {
  window.localStorage.clear()
  api.cancelPipelinePreview.mockReset()
  api.cancelPipelinePreview.mockResolvedValue(undefined)
  api.getPipelineBuild.mockReset()
  api.getPipelineSchedule.mockReset()
  api.getPipelineOutputSources.mockReset()
  api.listPipelineScheduleRuns.mockReset()
  api.listPipelineScheduleRuns.mockResolvedValue([])
  api.previewPipelineNode.mockReset()
})

afterEach(() => {
  cleanup()
  window.localStorage.clear()
})

describe('execution hook generation isolation', () => {
  it('does not let a deferred preview from pipeline A overwrite pipeline B', async () => {
    const pending = deferred<PipelinePreview>()
    api.previewPipelineNode.mockReturnValue(pending.promise)
    const { result, rerender } = renderHook(
      ({ pipelineId }: { pipelineId: string }) => usePipelinePreview({ pipelineId, draftRevision: 3 }),
      { initialProps: { pipelineId: 'pipeline-a' } },
    )

    let run!: Promise<PipelinePreview | null>
    act(() => {
      run = result.current.run('node-a')
    })
    await waitFor(() => expect(api.previewPipelineNode).toHaveBeenCalledTimes(1))

    rerender({ pipelineId: 'pipeline-b' })
    act(() => pending.resolve(preview('pipeline-a', 'node-a')))
    await act(async () => {
      await run
    })

    expect(result.current.preview).toBeNull()
  })

  it('drops an older output-source response when the requested dataset changes', async () => {
    const first = deferred<PipelineSource>()
    const second = deferred<PipelineSource>()
    api.getPipelineOutputSources.mockImplementation((_pipelineId, datasetId) =>
      datasetId === 'dataset-a' ? first.promise : second.promise)

    const { result, rerender } = renderHook(
      ({ datasetId }: { datasetId: string }) => useOutputSources({
        pipelineId: 'pipeline-a',
        datasetId,
        datasetVersionId: 'version-a',
      }),
      { initialProps: { datasetId: 'dataset-a' } },
    )
    await waitFor(() => expect(api.getPipelineOutputSources).toHaveBeenCalledWith(
      'pipeline-a', 'dataset-a', 'version-a'))

    rerender({ datasetId: 'dataset-b' })
    await waitFor(() => expect(api.getPipelineOutputSources).toHaveBeenCalledWith(
      'pipeline-a', 'dataset-b', 'version-a'))

    act(() => second.resolve(source('dataset-b')))
    await waitFor(() => expect(result.current.source?.dataset_id).toBe('dataset-b'))
    act(() => first.resolve(source('dataset-a')))
    await act(async () => {
      await Promise.resolve()
    })

    expect(result.current.source?.dataset_id).toBe('dataset-b')
  })

  it('does not restore a deferred build from pipeline A after switching to B', async () => {
    window.localStorage.setItem('ontoweave.data-pipeline.build.pipeline-a', 'build-a')
    const pending = deferred<PipelineBuild>()
    api.getPipelineBuild.mockReturnValue(pending.promise)
    const { result, rerender } = renderHook(
      ({ pipelineId }: { pipelineId: string }) => usePipelineBuild(pipelineId),
      { initialProps: { pipelineId: 'pipeline-a' } },
    )
    await waitFor(() => expect(api.getPipelineBuild).toHaveBeenCalledTimes(1))

    rerender({ pipelineId: 'pipeline-b' })
    act(() => pending.resolve(build('pipeline-a')))
    await act(async () => {
      await Promise.resolve()
    })

    expect(result.current.build).toBeNull()
    expect(result.current.isLoading).toBe(false)
  })

  it('does not reopen build details when a deferred open was closed first', async () => {
    const pending = deferred<PipelineBuild>()
    api.getPipelineBuild.mockReturnValue(pending.promise)
    const { result } = renderHook(() => usePipelineBuild('pipeline-a'))

    act(() => result.current.open('build-a'))
    await waitFor(() => expect(api.getPipelineBuild).toHaveBeenCalledTimes(1))
    act(() => result.current.setDetailsOpen(false))
    act(() => pending.resolve(build('pipeline-a')))
    await act(async () => {
      await Promise.resolve()
    })

    expect(result.current.detailsOpen).toBe(false)
  })

  it('cancels an in-flight preview when the panel closes', async () => {
    const pending = deferred<PipelinePreview>()
    api.previewPipelineNode.mockReturnValue(pending.promise)
    api.cancelPipelinePreview.mockResolvedValue(undefined)
    const { result } = renderHook(() => usePipelinePreview({
      pipelineId: 'pipeline-a',
      draftRevision: 3,
    }))

    let run!: Promise<PipelinePreview | null>
    act(() => {
      run = result.current.run('node-a')
    })
    await waitFor(() => expect(api.previewPipelineNode).toHaveBeenCalledTimes(1))
    act(() => result.current.close())

    expect(api.cancelPipelinePreview).toHaveBeenCalledWith('pipeline-a')
    act(() => pending.resolve(preview('pipeline-a', 'node-a')))
    await act(async () => {
      await run
    })
  })

  it('cancels the previous in-flight preview before running another node', async () => {
    const first = deferred<PipelinePreview>()
    api.previewPipelineNode
      .mockReturnValueOnce(first.promise)
      .mockResolvedValueOnce(preview('pipeline-a', 'node-b'))
    const { result } = renderHook(() => usePipelinePreview({
      pipelineId: 'pipeline-a',
      draftRevision: 3,
    }))

    let firstRun!: Promise<PipelinePreview | null>
    act(() => {
      firstRun = result.current.run('node-a')
    })
    await waitFor(() => expect(api.previewPipelineNode).toHaveBeenCalledTimes(1))

    let secondRun!: Promise<PipelinePreview | null>
    await act(async () => {
      secondRun = result.current.run('node-b')
      await Promise.resolve()
    })
    expect(api.cancelPipelinePreview).toHaveBeenCalledWith('pipeline-a')

    act(() => first.resolve(preview('pipeline-a', 'node-a')))
    await act(async () => {
      await Promise.all([firstRun, secondRun])
    })
  })

  it('drops a missing stored build and stops retrying on a 4xx response', async () => {
    window.localStorage.setItem('ontoweave.data-pipeline.build.pipeline-a', 'missing-build')
    api.getPipelineBuild.mockRejectedValue(
      new HttpError(404, '{"code":"NOT_FOUND","message":"构建不存在"}'),
    )
    const { result } = renderHook(() => usePipelineBuild('pipeline-a'))

    await waitFor(() => expect(result.current.error).toBe('构建不存在'))

    expect(result.current.build).toBeNull()
    expect(result.current.isLoading).toBe(false)
    expect(window.localStorage.getItem('ontoweave.data-pipeline.build.pipeline-a')).toBeNull()
    expect(api.getPipelineBuild).toHaveBeenCalledTimes(1)
  })

  it('does not let a deferred schedule from pipeline A overwrite pipeline B', async () => {
    const pending = deferred<PipelineSchedule>()
    api.getPipelineSchedule.mockImplementation((pipelineId: string) =>
      pipelineId === 'pipeline-a' ? pending.promise : Promise.resolve(schedule('pipeline-b')))
    const { result, rerender } = renderHook(
      ({ pipelineId }: { pipelineId: string }) => usePipelineSchedule(pipelineId),
      { initialProps: { pipelineId: 'pipeline-a' } },
    )
    await waitFor(() => expect(api.getPipelineSchedule).toHaveBeenCalledWith('pipeline-a'))

    rerender({ pipelineId: 'pipeline-b' })
    await waitFor(() => expect(result.current.schedule?.pipeline_id).toBe('pipeline-b'))
    act(() => pending.resolve(schedule('pipeline-a')))
    await act(async () => {
      await Promise.resolve()
    })

    expect(result.current.schedule?.pipeline_id).toBe('pipeline-b')
  })

  it('keeps build, preview, configuration, and schedule panels mutually exclusive', async () => {
    api.previewPipelineNode.mockResolvedValue(preview('pipeline-a', 'node-a'))
    api.getPipelineBuild.mockResolvedValue(build('pipeline-a'))
    const draft: PipelineDraftResponse = {
      pipeline_id: 'pipeline-a',
      revision: 3,
      definition: {
        schemaVersion: 1,
        nodes: [{
          id: 'node-a',
          type: 'filter',
          name: 'Filter',
          config: {},
          position: { x: 0, y: 0 },
        }],
        edges: [],
      },
    }
    const check = {
      pipeline_id: 'pipeline-a',
      revision: 3,
      stage: 'ready',
      blocking_issues: [],
      warnings: [],
      next_actions: [],
    }
    const { result } = renderHook(() => usePipelineExecutionUi({
      pipelineId: 'pipeline-a',
      draft,
      check,
      onBuildSucceeded: () => undefined,
    }))

    act(() => result.current.selectNode('node-a'))
    expect(result.current.selectedNode?.id).toBe('node-a')
    expect(result.current.build.detailsOpen).toBe(false)
    expect(result.current.scheduleOpen).toBe(false)

    act(() => result.current.openPreview('node-a', 3))
    expect(result.current.previewNode?.id).toBe('node-a')
    expect(result.current.selectedNode).toBeNull()

    act(() => result.current.openBuildDetails('build-a'))
    await waitFor(() => expect(result.current.build.detailsOpen).toBe(true))
    expect(result.current.previewNode).toBeNull()
    expect(result.current.selectedNode).toBeNull()
    expect(result.current.scheduleOpen).toBe(false)

    act(() => result.current.openSchedule())
    expect(result.current.scheduleOpen).toBe(true)
    expect(result.current.build.detailsOpen).toBe(false)
    expect(result.current.previewNode).toBeNull()
    expect(result.current.selectedNode).toBeNull()

    act(() => result.current.selectNode('node-a'))
    expect(result.current.selectedNode?.id).toBe('node-a')
    expect(result.current.scheduleOpen).toBe(false)
    expect(result.current.build.detailsOpen).toBe(false)
  })

  it('restores an active build from localStorage and stops polling in a terminal state', async () => {
    vi.useFakeTimers()
    try {
      window.localStorage.setItem('ontoweave.data-pipeline.build.pipeline-a', 'build-a')
      const active = { ...build('pipeline-a'), status: 'running', stage: 'executing' }
      const finished = { ...build('pipeline-a'), status: 'succeeded', stage: 'finished' }
      api.getPipelineBuild.mockResolvedValueOnce(active).mockResolvedValueOnce(finished)
      const { result } = renderHook(() => usePipelineBuild('pipeline-a'))

      await act(async () => {
        await Promise.resolve()
      })
      expect(result.current.build?.status).toBe('running')

      await act(async () => {
        vi.advanceTimersByTime(2_000)
        await Promise.resolve()
      })
      expect(result.current.build?.status).toBe('succeeded')

      await act(async () => {
        vi.advanceTimersByTime(6_000)
        await Promise.resolve()
      })
      expect(api.getPipelineBuild).toHaveBeenCalledTimes(2)
    } finally {
      vi.useRealTimers()
    }
  })
})
