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
})
