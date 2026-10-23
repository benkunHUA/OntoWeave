// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { OntologyProperty } from '../../../shared/types'
import { cleanup, fireEvent, render, screen, waitFor } from '../../../test/render'

const api = vi.hoisted(() => ({
  getObjectDetail: vi.fn(),
  listObjectRelationGroups: vi.fn(),
  listObjectRelations: vi.fn(),
}))

vi.mock('../../../shared/api/ontology', () => api)

import { ObjectDetailDrawer } from './ObjectDetailDrawer'

function property(id: string, name: string): OntologyProperty {
  return {
    id,
    name,
    datatype: 'text',
    required: false,
    is_array: false,
    is_primary_key: id === 'p1',
    is_title_key: false,
    enum_values: null,
    status: 'confirmed',
  }
}

describe('ObjectDetailDrawer', () => {
  beforeEach(() => {
    api.getObjectDetail.mockResolvedValue({
      primary_key: 'customer-1',
      title: 'Ada',
      properties: { p1: 'customer-1', p2: 'VIP' },
      index_version_id: 'version-1',
    })
    api.listObjectRelationGroups.mockResolvedValue([
      {
        relation_id: 'relation-1',
        direction: 'outgoing',
        direction_label: '出向',
        label: '订单',
        source_type_id: 'object-1',
        source_type_name: '客户',
        target_type_id: 'object-2',
        target_type_name: '订单',
        total: 6,
        items: Array.from({ length: 6 }, (_, index) => ({
          primary_key: `order-${index + 1}`,
          title: `订单 ${index + 1}`,
        })),
      },
    ])
    api.listObjectRelations.mockResolvedValue({
      items: [{ primary_key: 'order-6', title: '订单 6', index_version_id: 'version-1' }],
      total: 1,
      page: 1,
      page_size: 20,
    })
  })

  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
  })

  it('renders properties in definition order and uses an em dash for missing values', async () => {
    render(
      <ObjectDetailDrawer
        objectTypeId="object-1"
        primaryKey="customer-1"
        properties={[property('p1', '编号'), property('p3', '未映射属性'), property('p2', '等级')]}
        onClose={() => undefined}
      />,
    )

    await screen.findByText('编号')
    const terms = screen.getAllByRole('term').map((node) => node.textContent)
    expect(terms[0]).toContain('编号')
    expect(terms[1]).toContain('未映射属性')
    expect(terms[2]).toContain('等级')
    expect(screen.getByText('—')).toBeTruthy()
  })

  it('shows only five peers before opening the paginated relation view', async () => {
    render(
      <ObjectDetailDrawer
        objectTypeId="object-1"
        primaryKey="customer-1"
        properties={[property('p1', '编号')]}
        onClose={() => undefined}
      />,
    )

    await screen.findByText('订单 5')
    expect(screen.queryByText('订单 6')).toBeNull()
    fireEvent.click(screen.getByRole('button', { name: /查看全部/ }))

    await waitFor(() => expect(api.listObjectRelations).toHaveBeenCalledWith(
      'object-1',
      'relation-1',
      expect.objectContaining({ primary_key: 'customer-1', direction: 'outgoing', page: 1 }),
    ))
    expect(await screen.findByText('订单 6')).toBeTruthy()
  })
})
