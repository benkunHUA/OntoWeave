// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { OntologyResource } from '../../../../shared/types'
import { cleanup, fireEvent, render, screen, waitFor } from '../../../../test/render'

const api = vi.hoisted(() => ({
  getOntologyResourceDefinition: vi.fn(),
  listObjects: vi.fn(),
  getObjectDetail: vi.fn(),
  listObjectRelationGroups: vi.fn(),
  listObjectRelations: vi.fn(),
}))

vi.mock('../../../../shared/api/ontology', () => api)

import { ObjectDataTab } from './ObjectDataTab'

const resource: OntologyResource = {
  id: 'object-1',
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

function page(pageNumber: number, version: string, total = 40) {
  return {
    items: [{ primary_key: `customer-${pageNumber}`, title: 'Ada', index_version_id: version }],
    total,
    page: pageNumber,
    page_size: 20,
    index_version_id: version,
  }
}

describe('ObjectDataTab', () => {
  beforeEach(() => {
    api.getOntologyResourceDefinition.mockResolvedValue({
      id: 'object-1',
      name: '客户',
      kind: 'object_type',
      status: 'confirmed',
      description: null,
      properties: [{
        id: 'name',
        name: '名称',
        datatype: 'text',
        required: false,
        is_array: false,
        is_primary_key: false,
        is_title_key: false,
        enum_values: null,
        status: 'confirmed',
      }],
      cardinality: null,
      source_type_id: null,
      source_type_name: null,
      target_type_id: null,
      target_type_name: null,
      forward_label: null,
      reverse_label: null,
      foreign_key_property_id: null,
      foreign_key_property: null,
      source: null,
      target: null,
    })
    api.listObjects.mockResolvedValue(page(1, 'version-1'))
    api.getObjectDetail.mockResolvedValue({
      primary_key: 'customer-1',
      title: 'Ada',
      properties: { name: 'Ada' },
      index_version_id: 'version-1',
    })
    api.listObjectRelationGroups.mockResolvedValue([])
  })

  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
  })

  it('loads page one with a debounced search and total', async () => {
    render(<ObjectDataTab resource={resource} />)
    await screen.findByText('customer-1')
    expect(screen.getByText('共 40 条')).toBeTruthy()
    expect(api.listObjects).toHaveBeenLastCalledWith('object-1', {
      page: 1,
      page_size: 20,
      q: undefined,
      filters: [],
    })

    fireEvent.change(screen.getByLabelText('搜索对象'), { target: { value: 'Ada' } })
    expect(api.listObjects).toHaveBeenCalledTimes(1)
    await waitFor(() => expect(api.listObjects).toHaveBeenLastCalledWith('object-1', expect.objectContaining({
      page: 1,
      q: 'Ada',
    })), { timeout: 800 })
  })

  it('resets to page one and warns when the index version changes', async () => {
    api.listObjects
      .mockResolvedValueOnce(page(1, 'version-1'))
      .mockResolvedValueOnce(page(2, 'version-2'))
      .mockResolvedValue(page(1, 'version-2'))
    render(<ObjectDataTab resource={resource} />)
    await screen.findByText('customer-1')

    fireEvent.click(screen.getByRole('button', { name: '下一页' }))
    expect((await screen.findByRole('status')).textContent).toContain('对象已更新')
    await waitFor(() => expect(api.listObjects).toHaveBeenLastCalledWith('object-1', expect.objectContaining({
      page: 1,
    })))
  })

  it('opens the detail drawer from a row', async () => {
    render(<ObjectDataTab resource={resource} />)
    fireEvent.click(await screen.findByText('customer-1'))
    expect(await screen.findByRole('dialog', { name: '对象详情' })).toBeTruthy()
  })
})
