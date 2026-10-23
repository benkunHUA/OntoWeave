// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { BindingRead } from '../../../../shared/types'
import { cleanup, fireEvent, render, screen } from '../../../../test/render'
import {
  DATASET_ID,
  NAME_ID,
  columns,
  dataset,
  objectBinding,
  objectDefinition,
  relationDefinition,
  resource,
} from './testFixtures'

const api = vi.hoisted(() => ({
  generateObjectBinding: vi.fn(),
  generateRelationBinding: vi.fn(),
  getObjectBinding: vi.fn(),
  getOntologyResourceDefinition: vi.fn(),
  getRelationBinding: vi.fn(),
  listBindableDatasetColumns: vi.fn(),
  listBindableDatasets: vi.fn(),
  previewObjectBinding: vi.fn(),
  previewRelationBinding: vi.fn(),
  putObjectBinding: vi.fn(),
  putRelationBinding: vi.fn(),
  suggestObjectBinding: vi.fn(),
  suggestRelationBinding: vi.fn(),
  validateObjectBinding: vi.fn(),
  validateRelationBinding: vi.fn(),
}))

vi.mock('../../../../shared/api/ontology', () => api)

import { MappingCanvas } from './MappingCanvas'
import { buildMappingEdges } from './mappingModel'

const relationBinding: BindingRead = {
  binding_id: 'relation-binding',
  revision: 3,
  draft_dataset_kind: 'PIPELINE',
  draft_dataset_id: DATASET_ID,
  draft_mapping: {
    from_key_column: 'customer_id',
    from_key_datatype: 'text',
    to_key_column: 'order_id',
    to_key_datatype: 'integer',
  },
}

beforeEach(() => {
  vi.clearAllMocks()
  api.getOntologyResourceDefinition.mockResolvedValue(relationDefinition)
  api.getRelationBinding.mockResolvedValue(relationBinding)
  api.getObjectBinding.mockResolvedValue(null)
  api.listBindableDatasets.mockResolvedValue({ items: [dataset], total: 1, page: 1, size: 200 })
  api.listBindableDatasetColumns.mockResolvedValue(columns)
})

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('MappingCanvas', () => {
  it('renders both endpoint fields and builds two removable many-to-many edges', async () => {
    render(<MappingCanvas mode="relation" resource={resource('relation')} />)

    expect(await screen.findByText('源端主键')).toBeTruthy()
    expect(screen.getByText('目标端主键')).toBeTruthy()
    const edges = buildMappingEdges(
      'relation',
      relationBinding.draft_mapping as NonNullable<typeof relationBinding.draft_mapping>,
      relationDefinition,
      columns,
      vi.fn(),
    )
    expect(edges).toHaveLength(2)
    expect(edges.map((edge) => edge.id)).toEqual(['relation:from', 'relation:to'])
  })

  it('confirms before changing the primary key selector', async () => {
    api.getOntologyResourceDefinition.mockResolvedValue(objectDefinition)
    api.getObjectBinding.mockResolvedValue(objectBinding())
    render(<MappingCanvas mode="object" resource={resource()} />)

    const primary = await screen.findByLabelText('主键属性') as HTMLSelectElement
    fireEvent.change(primary, { target: { value: NAME_ID } })
    expect(screen.getByRole('dialog')).toBeTruthy()
    expect(screen.getByText('切换主键属性？')).toBeTruthy()

    fireEvent.click(screen.getByRole('button', { name: '确认切换' }))
    expect(primary.value).toBe(NAME_ID)
  })
})
