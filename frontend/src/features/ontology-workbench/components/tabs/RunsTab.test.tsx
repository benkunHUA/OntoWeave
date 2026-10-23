// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { IndexRun, OntologyResource } from '../../../../shared/types'
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '../../../../test/render'

const api = vi.hoisted(() => ({
  cancelIndexRun: vi.fn(),
  generateObjectBinding: vi.fn(),
  generateRelationBinding: vi.fn(),
  getIndexRun: vi.fn(),
  listObjectBindingRuns: vi.fn(),
  listRelationBindingRuns: vi.fn(),
}))

vi.mock('../../../../shared/api/ontology', () => api)

import { RunsTab } from './RunsTab'

const resource: OntologyResource = {
  id: 'object-1',
  name: '客户',
  status: 'confirmed',
  kind: 'object_type',
  binding: { revision: 3, draft_dataset_id: 'dataset-1' },
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

function run(id: string, status: IndexRun['status']): IndexRun {
  return {
    id,
    owner_id: 'owner-1',
    subject_kind: 'OBJECT_TYPE',
    subject_id: 'object-1',
    binding_id: 'binding-1',
    trigger_type: 'MANUAL',
    status,
    phase: status === 'RUNNING' ? 'WRITING' : null,
    target_dataset_kind: 'PIPELINE',
    target_dataset_version_id: 'dataset-version-1',
    mapping_revision: 2,
    requested_at: '2026-10-24T00:00:00Z',
    started_at: '2026-10-24T00:00:01Z',
    heartbeat_at: null,
    finished_at: status === 'RUNNING' ? null : '2026-10-24T00:01:00Z',
    error_code: status === 'FAILED' ? 'STAGE_VALIDATION_FAILED' : null,
    error_message: status === 'FAILED' ? 'duplicate key' : null,
    stats: {
      objectRowCount: 12,
      objectWarningCount: 2,
      samples: ['primary key duplicated: customer-1'],
      relations: [{ rowCount: 8, skippedCount: 1 }],
    },
  }
}

function renderTab(onRefresh = vi.fn()) {
  return render(
    <RunsTab
      resource={resource}
      run={null}
      isLoading={false}
      error={null}
      onRefresh={onRefresh}
    />,
  )
}

describe('RunsTab', () => {
  beforeEach(() => {
    api.listObjectBindingRuns.mockResolvedValue([run('run-1', 'RUNNING')])
    api.listRelationBindingRuns.mockResolvedValue([])
  })

  afterEach(() => {
    cleanup()
    vi.useRealTimers()
    vi.clearAllMocks()
  })

  it('renders counts and opens detail samples', async () => {
    renderTab()
    const row = await screen.findByRole('row', { name: /手动生成/ })
    expect(within(row).getByText('12')).toBeTruthy()
    expect(within(row).getByText('2 / 1')).toBeTruthy()

    fireEvent.click(row)
    expect(await screen.findByRole('dialog', { name: '运行详情' })).toBeTruthy()
    expect(screen.getByText('primary key duplicated: customer-1')).toBeTruthy()
  })

  it('polls active runs and stops after a terminal state', async () => {
    vi.useFakeTimers()
    api.listObjectBindingRuns
      .mockResolvedValueOnce([run('run-1', 'RUNNING')])
      .mockResolvedValue([run('run-1', 'SUCCEEDED')])
    renderTab()
    await act(async () => { await Promise.resolve() })
    expect(api.listObjectBindingRuns).toHaveBeenCalledTimes(1)

    await act(async () => {
      vi.advanceTimersByTime(2_000)
      await Promise.resolve()
    })
    expect(api.listObjectBindingRuns).toHaveBeenCalledTimes(2)

    await act(async () => {
      vi.advanceTimersByTime(6_000)
      await Promise.resolve()
    })
    expect(api.listObjectBindingRuns).toHaveBeenCalledTimes(2)
  })

  it('cancels only after confirmation and disables terminal rows', async () => {
    const cancelled = run('run-1', 'CANCELLED')
    api.listObjectBindingRuns
      .mockResolvedValueOnce([run('run-1', 'RUNNING')])
      .mockResolvedValue([cancelled])
    api.cancelIndexRun.mockResolvedValue(cancelled)
    const onRefresh = vi.fn()
    renderTab(onRefresh)
    const row = await screen.findByRole('row', { name: /手动生成/ })
    fireEvent.click(within(row).getByRole('button', { name: /取消/ }))
    expect(await screen.findByText('确认取消运行')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: /确认取消/ }))

    await waitFor(() => expect(api.cancelIndexRun).toHaveBeenCalledWith('run-1', expect.any(String)))
    expect(onRefresh).toHaveBeenCalled()
    await waitFor(() => expect(screen.getByRole('button', { name: /取消/ })).toHaveProperty('disabled', true))
  })

  it('routes failed retries through the generation confirmation', async () => {
    const failed = run('run-1', 'FAILED')
    api.listObjectBindingRuns.mockResolvedValue([failed])
    api.generateObjectBinding.mockResolvedValue({ run_id: 'run-2' })
    api.getIndexRun.mockResolvedValue(run('run-2', 'QUEUED'))
    renderTab()
    const row = await screen.findByRole('row', { name: /手动生成/ })
    fireEvent.click(within(row).getByRole('button', { name: /重试/ }))
    expect(await screen.findByText('确认全量生成')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: /确认生成/ }))

    await waitFor(() => expect(api.generateObjectBinding).toHaveBeenCalledWith(
      'object-1',
      { mapping_revision: 3 },
      expect.any(String),
    ))
  })
})
