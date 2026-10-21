// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { HttpError } from '../../../../shared/api/client'
import type { Pipeline, PipelineSchedule, PipelineScheduleRun } from '../../../../shared/types'
import { ScheduleDrawer } from './ScheduleDrawer'

const api = vi.hoisted(() => ({
  getPipelineSchedule: vi.fn(),
  listPipelineScheduleRuns: vi.fn(),
  pausePipelineSchedule: vi.fn(),
  putPipelineSchedule: vi.fn(),
  resumePipelineSchedule: vi.fn(),
}))
vi.mock('../../../../shared/api', () => api)

const pipeline: Pipeline = {
  id: 'pipeline-1',
  conversation_id: 'conversation-1',
  name: '订单整理',
  status: 'draft',
  draft_revision: 4,
  current_version_id: null,
  current_version_number: null,
  current_version_draft_revision: null,
  created_at: null,
  updated_at: null,
}

const schedule: PipelineSchedule = {
  id: 'schedule-1',
  pipeline_id: 'pipeline-1',
  name: '订单每日调度',
  status: 'active',
  trigger_type: 'schedule',
  cron_expression: '0 9 * * *',
  timezone: 'Asia/Shanghai',
  next_run_at: '2026-10-22T01:00:00Z',
  last_run_at: null,
  paused_at: null,
}

const run: PipelineScheduleRun = {
  id: 'run-1',
  schedule_id: 'schedule-1',
  pipeline_id: 'pipeline-1',
  status: 'ignored',
  trigger_type: 'schedule',
  scheduled_for: '2026-10-21T01:00:00Z',
  build_id: null,
  logic_hash: 'hash',
  reason_code: 'NO_CHANGE',
  message: null,
  started_at: null,
  finished_at: null,
}

beforeEach(() => {
  api.getPipelineSchedule.mockReset()
  api.listPipelineScheduleRuns.mockReset()
  api.pausePipelineSchedule.mockReset()
  api.putPipelineSchedule.mockReset()
  api.resumePipelineSchedule.mockReset()
  api.listPipelineScheduleRuns.mockResolvedValue([])
})

afterEach(cleanup)

describe('ScheduleDrawer', () => {
  it('treats a missing schedule as uncreated and confirms before creating it', async () => {
    api.getPipelineSchedule
      .mockRejectedValueOnce(new HttpError(404, '{"code":"NOT_FOUND"}'))
      .mockResolvedValue(schedule)
    api.putPipelineSchedule.mockResolvedValue(schedule)

    render(<ScheduleDrawer
      open
      pipeline={pipeline}
      draftRevision={4}
      onClose={() => undefined}
      onOpenBuild={() => undefined}
    />)

    expect(await screen.findByText('未创建调度')).toBeTruthy()
    expect(screen.getByText('首次构建后才能定时运行')).toBeTruthy()

    fireEvent.click(screen.getByRole('button', { name: '创建调度' }))
    expect(screen.getAllByRole('dialog')).toHaveLength(2)
    expect(api.putPipelineSchedule).not.toHaveBeenCalled()

    fireEvent.click(screen.getByRole('button', { name: '确认创建' }))
    await waitFor(() => expect(api.putPipelineSchedule).toHaveBeenCalledTimes(1))
  })

  it('confirms pause and explains skipped schedule runs', async () => {
    api.getPipelineSchedule.mockResolvedValue(schedule)
    api.listPipelineScheduleRuns.mockResolvedValue([
      run,
      { ...run, id: 'run-2', status: 'failed', reason_code: 'BUILD_FAILED' },
    ])
    api.pausePipelineSchedule.mockResolvedValue({ ...schedule, status: 'paused' })

    render(<ScheduleDrawer
      open
      pipeline={{
        ...pipeline,
        status: 'active',
        current_version_id: 'version-1',
        current_version_number: 2,
        current_version_draft_revision: 3,
      }}
      draftRevision={4}
      onClose={() => undefined}
      onOpenBuild={() => undefined}
    />)

    expect(await screen.findByText('输入和逻辑未变化，已跳过')).toBeTruthy()
    expect(screen.getByText('已跳过，未执行')).toBeTruthy()
    expect(screen.getByText('未开始执行即失败')).toBeTruthy()
    expect(screen.getAllByText(/时间触发/).length).toBeGreaterThan(0)
    expect(screen.getAllByText(/Asia\/Shanghai/).length).toBeGreaterThan(0)
    expect(screen.getByDisplayValue('Asia/Shanghai').getAttribute('list'))
      .toBe('pipeline-timezones')
    expect(screen.getByText('调度仍使用已发布版本 v2')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: '暂停调度' }))
    expect(screen.getAllByRole('dialog')).toHaveLength(2)

    fireEvent.click(screen.getByRole('button', { name: '确认暂停' }))
    await waitFor(() => expect(api.pausePipelineSchedule).toHaveBeenCalledTimes(1))
  })
})
