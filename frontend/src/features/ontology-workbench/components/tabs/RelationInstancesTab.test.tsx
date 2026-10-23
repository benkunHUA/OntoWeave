// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { OntologyResource } from '../../../../shared/types'
import { cleanup, fireEvent, render, screen, waitFor } from '../../../../test/render'

const api = vi.hoisted(() => ({
  getOntologyResourceDefinition: vi.fn(),
  listRelationInstances: vi.fn(),
}))

vi.mock('../../../../shared/api/ontology', () => api)

import { RelationInstancesTab } from './RelationInstancesTab'

const resource: OntologyResource = {
  id: 'relation-1',
  name: '客户订单',
  status: 'confirmed',
  kind: 'relation',
  binding: null,
  cardinality: 'many_to_many',
  relation_kind: 'MANY_TO_MANY',
  source_type_id: 'object-1',
  source_type_name: '客户',
  target_type_id: 'object-2',
  target_type_name: '订单',
  forward_label: '订单',
  reverse_label: '客户',
  direction_label: '客户 → 订单',
  foreign_key_property_id: null,
}

describe('RelationInstancesTab', () => {
  beforeEach(() => {
    api.getOntologyResourceDefinition.mockResolvedValue({
      id: 'relation-1',
      name: '客户订单',
      kind: 'relation',
      status: 'confirmed',
      description: null,
      properties: [],
      cardinality: 'many_to_many',
      source_type_id: 'object-1',
      source_type_name: '客户',
      target_type_id: 'object-2',
      target_type_name: '订单',
      forward_label: '订单',
      reverse_label: '客户',
      foreign_key_property_id: null,
      foreign_key_property: null,
      source: null,
      target: null,
    })
    api.listRelationInstances.mockResolvedValue({
      items: [{
        from_object_type_id: 'object-1',
        from_primary_key: 'customer-1',
        to_object_type_id: 'object-2',
        to_primary_key: 'order-1',
        index_version_id: 'version-1',
      }],
      total: 21,
      page: 1,
      page_size: 20,
    })
  })

  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
  })

  it('renders both endpoints, total and backend filters', async () => {
    render(<RelationInstancesTab resource={resource} />)
    expect(await screen.findByText('customer-1')).toBeTruthy()
    expect(screen.getByText('order-1')).toBeTruthy()
    expect(screen.getByText('共 21 条')).toBeTruthy()

    fireEvent.change(screen.getByLabelText('来源主键'), { target: { value: 'customer-1' } })
    fireEvent.change(screen.getByLabelText('目标主键'), { target: { value: 'order-1' } })
    fireEvent.click(screen.getByRole('button', { name: /查询/ }))

    await waitFor(() => expect(api.listRelationInstances).toHaveBeenLastCalledWith('relation-1', {
      page: 1,
      page_size: 20,
      from_primary_key: 'customer-1',
      to_primary_key: 'order-1',
    }))
  })

  it('clears endpoint filters', async () => {
    render(<RelationInstancesTab resource={resource} />)
    await screen.findByText('customer-1')
    fireEvent.change(screen.getByLabelText('来源主键'), { target: { value: 'customer-1' } })
    fireEvent.click(screen.getByRole('button', { name: /查询/ }))
    await waitFor(() => expect(api.listRelationInstances).toHaveBeenLastCalledWith(
      'relation-1',
      expect.objectContaining({ from_primary_key: 'customer-1' }),
    ))

    fireEvent.click(screen.getByRole('button', { name: '清空条件' }))
    await waitFor(() => expect(api.listRelationInstances).toHaveBeenLastCalledWith('relation-1', {
      page: 1,
      page_size: 20,
      from_primary_key: undefined,
      to_primary_key: undefined,
    }))
  })
})
