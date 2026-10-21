// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { HttpError } from '../../../../shared/api/client'
import type { PipelineBuild, PipelineSource } from '../../../../shared/types'
import { BuildDetailsDrawer } from './BuildDetailsDrawer'

const api = vi.hoisted(() => ({ getPipelineOutputSources: vi.fn() }))
vi.mock('../../../../shared/api', () => api)

const source: PipelineSource = {
  pipeline_id: 'pipeline-1',
  dataset_id: 'dataset-1',
  version_id: 'dataset-version-1',
  output: {
    output_id: 'output-1',
    name: 'orders_clean',
    version_number: 3,
    row_count: 42,
    published_at: '2026-10-21T10:02:00Z',
  },
  build: {
    build_id: 'build-1',
    status: 'succeeded',
    trigger: 'manual',
    started_at: '2026-10-21T10:00:00Z',
    finished_at: '2026-10-21T10:02:00Z',
  },
  pipeline_version: {
    version_id: 'pipeline-version-1',
    version_number: 5,
    created_at: '2026-10-21T10:00:00Z',
  },
  inputs: [{
    dataset_kind: 'sync',
    dataset_id: 'input-dataset-1',
    dataset_version_id: 'input-version-1',
    dataset_version_number: 9,
    schema_hash: 'hash',
    dataset_name: 'orders_raw',
    connection_name: 'ERP',
    source_schema_name: 'sales',
    source_table_name: 'orders',
    metadata_status: 'available',
  }],
}

const build: PipelineBuild = {
  id: 'build-1',
  pipeline_id: 'pipeline-1',
  pipeline_version_id: 'pipeline-version-1',
  status: 'running',
  stage: 'checking_expectations',
  trigger: 'manual',
  error_code: null,
  error_message: null,
  log_uri: 'https://logs.example/build-1',
  started_at: '2026-10-21T10:00:00Z',
  finished_at: null,
  outputs: [{
    id: 'build-output-1',
    output_id: 'output-1',
    dataset_id: 'dataset-1',
    dataset_version_id: 'dataset-version-1',
    output_type: 'dataset',
    status: 'succeeded',
    stage: 'checking_expectations',
    engine_job_id: 'flink-job-1',
    log_uri: null,
    error_code: null,
    error_message: null,
    result: {
      expectations: [{
        type: 'ROW_COUNT_RANGE',
        passed: true,
        actual: 42,
        expected: { min: 1, max: 100 },
        failedRows: 0,
        message: 'ok',
      }],
    },
    stats: { rowCount: 42, elapsedMs: 1200 },
    started_at: '2026-10-21T10:00:00Z',
    finished_at: '2026-10-21T10:01:00Z',
  }],
}

beforeEach(() => {
  api.getPipelineOutputSources.mockResolvedValue(source)
})

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

describe('BuildDetailsDrawer', () => {
  it('opens the published output dataset and shows its single-hop source projection', async () => {
    render(<BuildDetailsDrawer
      pipelineId="pipeline-1"
      build={build}
      outputNames={{ 'output-1': '订单输出' }}
      isLoading={false}
      error={null}
      onClose={() => undefined}
      onCancel={async () => true}
    />)

    expect(screen.getByText('订单输出')).toBeTruthy()
    expect(screen.getAllByText('检查数据期望').length).toBeGreaterThan(0)
    expect(screen.getByText(/flink-job-1/)).toBeTruthy()
    expect(screen.getByText('ROW_COUNT_RANGE')).toBeTruthy()
    expect(screen.getByText('日志尚未持久化，以下为诊断信息')).toBeTruthy()
    expect(screen.getByRole('link', { name: '打开完整日志' }).getAttribute('href'))
      .toBe('https://logs.example/build-1')
    expect(api.getPipelineOutputSources).not.toHaveBeenCalled()

    fireEvent.click(screen.getByRole('button', { name: '查看输出数据集' }))
    expect(screen.getByText('输出数据集详情')).toBeTruthy()

    await waitFor(() => expect(screen.getByText('orders_raw')).toBeTruthy())
    expect(screen.getByText(/v9/)).toBeTruthy()
    expect(screen.getByText(/ERP/)).toBeTruthy()
    expect(screen.getByText(/sales\.orders/)).toBeTruthy()
    expect(screen.queryByText(/下游影响/)).toBeNull()
    expect(screen.queryByText(/祖先/)).toBeNull()
    expect(api.getPipelineOutputSources).toHaveBeenCalledWith(
      'pipeline-1',
      'dataset-1',
      'dataset-version-1',
    )

    fireEvent.click(screen.getAllByRole('button', { name: '关闭' }).at(-1)!)
    expect(screen.queryByText('输出数据集详情')).toBeNull()
  })

  it('keeps cancel retryable and shows a structured error when the request fails', async () => {
    const onCancel = vi.fn()
      .mockRejectedValueOnce(new HttpError(409, '{"code":"PIPELINE_ARCHIVED","message":"流程已归档"}'))
      .mockResolvedValueOnce(true)
    render(<BuildDetailsDrawer
      pipelineId="pipeline-1"
      build={build}
      outputNames={{}}
      isLoading={false}
      error={null}
      onClose={() => undefined}
      onCancel={onCancel}
    />)

    const button = screen.getByRole('button', { name: '取消构建' }) as HTMLButtonElement
    fireEvent.click(button)
    const alert = await screen.findByRole('alert')
    expect(alert.textContent).toContain('流程已归档')
    expect(button.disabled).toBe(false)

    fireEvent.click(button)
    await waitFor(() => expect(onCancel).toHaveBeenCalledTimes(2))
    await waitFor(() => expect(screen.getByText(/已发送取消请求/)).toBeTruthy())
  })

  it('uses the output log URI when the worker persisted one', () => {
    render(<BuildDetailsDrawer
      pipelineId="pipeline-1"
      build={{
        ...build,
        log_uri: null,
        outputs: [{ ...build.outputs[0], log_uri: 's3://logs/output-1.txt' }],
      }}
      outputNames={{}}
      isLoading={false}
      error={null}
      onClose={() => undefined}
      onCancel={async () => true}
    />)

    const link = screen.getAllByRole('link', { name: '打开完整日志' })
      .find((item) => item.getAttribute('href') === 's3://logs/output-1.txt')
    expect(link).toBeTruthy()
  })

  it('skips the source panel until the output has a published dataset version', () => {
    render(<BuildDetailsDrawer
      pipelineId="pipeline-1"
      build={{
        ...build,
        outputs: [{ ...build.outputs[0], dataset_version_id: null }],
      }}
      outputNames={{}}
      isLoading={false}
      error={null}
      onClose={() => undefined}
      onCancel={async () => true}
    />)

    expect(api.getPipelineOutputSources).not.toHaveBeenCalled()
    expect(screen.queryByRole('button', { name: '查看输出数据集' })).toBeNull()
  })
})
