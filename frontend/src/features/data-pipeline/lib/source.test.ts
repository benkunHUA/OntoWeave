import { describe, expect, it } from 'vitest'
import type { PipelineSourceInput } from '../../../shared/types'
import { sourceInputView } from './source'

function input(overrides: Partial<PipelineSourceInput>): PipelineSourceInput {
  return {
    dataset_kind: 'sync',
    dataset_id: 'dataset-1',
    dataset_version_id: 'version-1',
    dataset_version_number: 3,
    schema_hash: 'hash',
    dataset_name: 'demo.orders',
    connection_name: 'ERP',
    source_schema_name: 'demo',
    source_table_name: 'orders',
    metadata_status: 'available',
    ...overrides,
  }
}

describe('source input visibility', () => {
  it('hides the dataset name, connection and source table for restricted metadata', () => {
    const view = sourceInputView(input({ metadata_status: 'restricted' }))

    expect(view.showConnection).toBe(false)
    expect(view.connection).toBeNull()
    expect(view.sourceTable).toBeNull()
    expect(view.title).toBe('dataset-1')
    expect(view.note).toBe('元数据无权限')
  })

  it('shows the dataset id and unavailable note for missing metadata', () => {
    const view = sourceInputView(input({
      metadata_status: 'missing',
      dataset_name: null,
      connection_name: null,
      source_schema_name: null,
      source_table_name: null,
    }))

    expect(view.title).toBe('dataset-1')
    expect(view.note).toBe('元数据不可用')
    expect(view.showConnection).toBe(false)
  })
})
