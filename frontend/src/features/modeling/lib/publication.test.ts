import { describe, expect, it } from 'vitest'
import type { Changeset, ChangesetOp, GraphSnapshot } from '../../../shared/types'
import { PUBLISH_COPY, publicationBlockedReason } from './publication'

function snapshot(overrides: Partial<GraphSnapshot> = {}): GraphSnapshot {
  return {
    graph: { id: 'graph-1', name: '销售' },
    nodes: [
      {
        id: 'node-1',
        name: '客户',
        description: null,
        status: 'confirmed',
        properties: [
          {
            id: 'property-1',
            name: '客户名',
            datatype: 'text',
            required: true,
            is_array: false,
            is_primary_key: true,
            is_title_key: true,
            enum_values: null,
            status: 'confirmed',
          },
        ],
      },
    ],
    relations: [],
    actions: [],
    ...overrides,
  }
}

describe('publicationBlockedReason', () => {
  function openChangeset(ops: ChangesetOp[] = []): Changeset {
    return { id: 'changeset-1', graph_id: 'graph-1', status: 'open', ops }
  }

  it('allows a confirmed graph with no open changeset', () => {
    expect(publicationBlockedReason(snapshot(), null)).toBeNull()
  })

  it('ignores an empty open changeset left by the runtime', () => {
    expect(publicationBlockedReason(snapshot(), openChangeset())).toBeNull()
  })

  it('blocks while a changeset still needs confirmation', () => {
    const changes = openChangeset([
      { id: 'op-1', op: 'update', target_id: 'node-1', payload: {} },
    ])
    expect(publicationBlockedReason(snapshot(), changes)).toBe(PUBLISH_COPY.openChangeset)
  })

  it('does not block for view-only changes', () => {
    const changes = openChangeset([
      { id: 'op-1', op: 'view', target_id: null, payload: {} },
    ])
    expect(publicationBlockedReason(snapshot(), changes)).toBeNull()
  })

  it('counts draft nodes, properties and relations', () => {
    const graph = snapshot()
    graph.nodes[0].properties[0].status = 'draft'
    graph.relations.push({
      id: 'relation-1',
      name: '下单',
      source_type_id: 'node-1',
      target_type_id: 'node-2',
      direction: 'forward',
      cardinality: 'many',
      forward_label: null,
      reverse_label: null,
      foreign_key_property_id: null,
      status: 'draft',
    })

    expect(publicationBlockedReason(graph, null)).toBe(PUBLISH_COPY.draftElements(2))
  })

  it('requires at least one object type', () => {
    expect(publicationBlockedReason(snapshot({ nodes: [] }), null)).toBe(
      PUBLISH_COPY.noObjects,
    )
  })
})
