import { describe, expect, it } from 'vitest'
import { buildFlowElements } from './flow'
import type { GraphSnapshot } from '../../../shared/types'

function node(id: string, name: string) {
  return {
    id,
    name,
    description: null,
    status: 'confirmed' as const,
    properties: [],
  }
}

function overlaps(
  left: { x: number; y: number },
  right: { x: number; y: number },
) {
  return Math.abs(left.x - right.x) < 224 && Math.abs(left.y - right.y) < 116
}

describe('buildFlowElements', () => {
  it('places new nodes away from existing saved coordinates', () => {
    const snapshot: GraphSnapshot = {
      graph: { id: 'g1', name: '测试图' },
      nodes: [node('a', '节点 A'), node('b', '节点 B'), node('c', '新节点 C')],
      relations: [],
      actions: [],
    }
    const spec = {
      layout: { kind: 'grid' as const, options: {} },
      nodes: {
        a: { x: 0, y: 188 },
      },
    }

    const { nodes } = buildFlowElements(snapshot, spec, null)
    const placed = Object.fromEntries(
      nodes.map((item) => [item.id, item.position]),
    )

    expect(placed.a).toEqual({ x: 0, y: 188 })
    expect(overlaps(placed.c, placed.a)).toBe(false)
  })

  it('keeps every computed node separated', () => {
    const snapshot: GraphSnapshot = {
      graph: { id: 'g1', name: '测试图' },
      nodes: Array.from({ length: 6 }, (_, index) => node(`${index}`, `节点 ${index}`)),
      relations: [],
      actions: [],
    }

    const { nodes } = buildFlowElements(
      snapshot,
      { layout: { kind: 'grid', options: {} }, nodes: {} },
      null,
    )

    for (let left = 0; left < nodes.length; left += 1) {
      for (let right = left + 1; right < nodes.length; right += 1) {
        expect(overlaps(nodes[left].position, nodes[right].position)).toBe(false)
      }
    }
  })

  it('places action nodes under the object graph and wires involves edges', () => {
    const snapshot: GraphSnapshot = {
      graph: { id: 'g1', name: '测试图' },
      nodes: [node('a', '节点 A'), node('b', '节点 B')],
      relations: [],
      actions: [
        {
          id: 'act1',
          name: '指派工单',
          description: null,
          status: 'draft',
          involves: [
            { kind: 'object_type', id: 'a' },
            { kind: 'object_type', id: 'b' },
          ],
        },
      ],
    }

    const { nodes, edges } = buildFlowElements(
      snapshot,
      {
        layout: { kind: 'grid', options: {} },
        nodes: { a: { x: 0, y: 0 }, b: { x: 400, y: 0 } },
      },
      null,
    )

    const action = nodes.find((item) => item.id === 'act1')
    expect(action?.type).toBe('action')
    expect(action?.data).toMatchObject({ draft: true })
    // 排在对象图下方，横向落在两个对象中间（两个对象的中心是 312）。
    expect(action?.position.y).toBeGreaterThan(116)
    expect((action?.position.x ?? 0) + 252 / 2).toBe(312)
    expect(edges).toContainEqual(
      expect.objectContaining({
        id: 'involves:act1:object_type:a',
        source: 'act1',
        target: 'a',
        sourceHandle: 'left',
        className: 'edge-involves',
        data: { involves: true },
      }),
    )
    expect(edges).toContainEqual(
      expect.objectContaining({
        id: 'involves:act1:object_type:b',
        sourceHandle: 'right',
      }),
    )
  })

  it('drops an involves edge whose element is not on the canvas', () => {
    const snapshot: GraphSnapshot = {
      graph: { id: 'g1', name: '测试图' },
      nodes: [node('a', '节点 A'), node('b', '节点 B')],
      relations: [],
      actions: [
        {
          id: 'act1',
          name: '指派工单',
          description: null,
          status: 'confirmed',
          involves: [
            { kind: 'object_type', id: 'a' },
            { kind: 'object_type', id: 'missing' },
          ],
        },
      ],
    }
    const spec = {
      layout: { kind: 'grid' as const, options: {} },
      nodes: {},
      groups: [{ id: 'grp', label: '域', nodeIds: ['b'] }],
      collapsed: ['grp'],
    }

    const { edges } = buildFlowElements(snapshot, spec, null)
    expect(edges.map((edge) => edge.id)).toEqual(['involves:act1:object_type:a'])
  })
})
