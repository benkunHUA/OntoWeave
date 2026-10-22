// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen, waitFor } from '../../../../test/render'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { PipelineSource } from '../../../../shared/types'
import { OutputSourcePanel } from './OutputSourcePanel'

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
    log_uri: 'https://flink.example/#/job/build-job/exceptions',
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

beforeEach(() => api.getPipelineOutputSources.mockResolvedValue(source))
afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

describe('OutputSourcePanel', () => {
  it('renders output, build, pipeline version and one-hop direct inputs', async () => {
    render(<OutputSourcePanel
      pipelineId="pipeline-1"
      datasetId="dataset-1"
      datasetVersionId="dataset-version-1"
    />)

    expect(await screen.findByText('orders_clean')).toBeTruthy()
    expect(screen.getByText(/输出版本 v3/)).toBeTruthy()
    expect(screen.getByText(/42 行/)).toBeTruthy()
    expect(screen.getByText('流程版本')).toBeTruthy()
    expect(screen.getByText(/v5/)).toBeTruthy()
    expect(screen.getByText('orders_raw')).toBeTruthy()
    expect(screen.getByText(/ERP/)).toBeTruthy()
    expect(screen.getByText(/sales\.orders/)).toBeTruthy()
  })

  it('hides restricted connection metadata and explains missing metadata', async () => {
    api.getPipelineOutputSources.mockResolvedValue({
      ...source,
      inputs: [
        { ...source.inputs[0], metadata_status: 'restricted' },
        {
          ...source.inputs[0],
          dataset_id: 'missing-dataset',
          dataset_name: null,
          connection_name: null,
          source_schema_name: null,
          source_table_name: null,
          metadata_status: 'missing',
        },
      ],
    })

    render(<OutputSourcePanel
      pipelineId="pipeline-1"
      datasetId="dataset-1"
      datasetVersionId="dataset-version-1"
    />)

    await waitFor(() => expect(screen.getByText('元数据无权限')).toBeTruthy())
    expect(screen.getByText('input-dataset-1')).toBeTruthy()
    expect(screen.queryByText('orders_raw')).toBeNull()
    expect(screen.queryByText('ERP')).toBeNull()
    expect(screen.queryByText('sales.orders')).toBeNull()
    expect(screen.getByText('missing-dataset')).toBeTruthy()
    expect(screen.getByText('元数据不可用')).toBeTruthy()
  })

  it('opens build details and exposes the persisted build log', async () => {
    const onOpenBuild = vi.fn()
    render(<OutputSourcePanel
      pipelineId="pipeline-1"
      datasetId="dataset-1"
      datasetVersionId="dataset-version-1"
      onOpenBuild={onOpenBuild}
    />)

    expect(await screen.findByRole('link', { name: '打开构建日志' }))
      .toHaveProperty('href', 'https://flink.example/#/job/build-job/exceptions')
    fireEvent.click(screen.getByRole('button', { name: '查看构建详情' }))
    expect(onOpenBuild).toHaveBeenCalledWith('build-1')
  })

  it('degrades explicitly when the build projection is missing', async () => {
    api.getPipelineOutputSources.mockResolvedValue({ ...source, build: null })
    render(<OutputSourcePanel
      pipelineId="pipeline-1"
      datasetId="dataset-1"
      datasetVersionId="dataset-version-1"
    />)

    expect(await screen.findByText('构建信息不可用')).toBeTruthy()
  })
})
