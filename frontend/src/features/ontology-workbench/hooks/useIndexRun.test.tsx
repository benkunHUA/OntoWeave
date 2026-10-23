// @vitest-environment jsdom

import { act, cleanup, renderHook } from '../../../test/render'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { IndexRun, OntologyResource } from '../../../shared/types'

const api = vi.hoisted(() => ({
  getIndexRun: vi.fn(),
  listObjectBindingRuns: vi.fn(),
  listRelationBindingRuns: vi.fn(),
  cancelIndexRun: vi.fn(),
}))

vi.mock('../../../shared/api/ontology', () => api)

import { useIndexRun } from './useIndexRun'

function resource(id = 'object-1'): OntologyResource {
  return {
    id,
    name: '客户',
    status: 'confirmed',
    kind: 'object_type',
    binding: null,
    cardinality: null,
    relation_kind: null,
    source_type_id: null,
    source_type_name: null,
    target_type_id: null,
    target_type_name: null,
    forward_label: null,
    reverse_label: null,
    direction_label: null,
    foreign_key_property_id: null,
  }
}

function run(id: string, status: IndexRun['status']): IndexRun {
  return {
    id,
    owner_id: 'owner-1',
    subject_kind: 'OBJECT_TYPE',
    subject_id: 'object-1',
    binding_id: 'binding-1',
    trigger_type: 'MANUAL',
    status,
    phase: null,
    target_dataset_kind: 'PIPELINE',
    target_dataset_version_id: 'version-1',
    mapping_revision: 1,
    requested_at: '2026-10-24T00:00:00Z',
    started_at: null,
    heartbeat_at: null,
    finished_at: null,
    error_code: null,
    error_message: null,
    stats: {},
  }
}

describe('useIndexRun', () => {
  let poll: (() => void) | null

  beforeEach(() => {
    poll = null
    api.getIndexRun.mockReset()
    api.listObjectBindingRuns.mockReset()
    api.listRelationBindingRuns.mockReset()
    api.cancelIndexRun.mockReset()
    vi.spyOn(window, 'setInterval').mockImplementation((handler) => {
      poll = handler as () => void
      return 1
    })
    vi.spyOn(window, 'clearInterval').mockImplementation(() => {})
  })

  afterEach(() => {
    cleanup()
    vi.restoreAllMocks()
  })

  async function settle() {
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 0))
    })
  }

  it('does not poll when the latest run is already terminal', async () => {
    api.listObjectBindingRuns.mockResolvedValue([run('run-1', 'SUCCEEDED')])

    const { result } = renderHook(() => useIndexRun(resource()))
    await settle()

    expect(result.current.run?.status).toBe('SUCCEEDED')
    expect(result.current.isPolling).toBe(false)
    expect(window.setInterval).not.toHaveBeenCalled()
    expect(api.getIndexRun).not.toHaveBeenCalled()
  })

  it('polls every two seconds and stops after a terminal status', async () => {
    api.listObjectBindingRuns.mockResolvedValue([run('run-1', 'QUEUED')])
    api.getIndexRun
      .mockResolvedValueOnce(run('run-1', 'RUNNING'))
      .mockResolvedValueOnce(run('run-1', 'SUCCEEDED'))

    const { result } = renderHook(() => useIndexRun(resource()))
    await settle()
    expect(poll).toBeTypeOf('function')
    expect(result.current.isPolling).toBe(true)

    await act(async () => {
      poll?.()
      await new Promise((resolve) => setTimeout(resolve, 0))
    })
    expect(result.current.run?.status).toBe('RUNNING')
    expect(api.getIndexRun).toHaveBeenCalledTimes(1)

    await act(async () => {
      poll?.()
      await new Promise((resolve) => setTimeout(resolve, 0))
    })
    expect(result.current.run?.status).toBe('SUCCEEDED')
    expect(result.current.isPolling).toBe(false)
    expect(api.getIndexRun).toHaveBeenCalledTimes(2)
    expect(window.clearInterval).toHaveBeenCalled()
  })

  it('cancels polling when the selected resource changes', async () => {
    api.listObjectBindingRuns.mockResolvedValue([run('run-1', 'RUNNING')])
    api.getIndexRun.mockResolvedValue(run('run-1', 'RUNNING'))

    const { rerender } = renderHook(({ item }) => useIndexRun(item), {
      initialProps: { item: resource('object-1') },
    })
    await settle()
    expect(poll).toBeTypeOf('function')

    api.listObjectBindingRuns.mockResolvedValue([])
    rerender({ item: resource('object-2') })
    await settle()
    expect(api.listObjectBindingRuns).toHaveBeenCalledTimes(2)
    expect(window.clearInterval).toHaveBeenCalled()
    expect(api.getIndexRun).not.toHaveBeenCalled()
  })
})
