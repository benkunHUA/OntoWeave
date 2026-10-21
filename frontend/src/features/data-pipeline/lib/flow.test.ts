import { describe, expect, it } from 'vitest'
import { changedPositionUpdates, draftToFlow } from './flow'
import { FILTER_A, INPUT_ID, OUTPUT_ID, catalog, draftFixture } from './testFixtures'

describe('draftToFlow', () => {
  it('builds nodes and edges from the wrapped draft without mutating positions', () => {
    const draft = draftFixture()
    const result = draftToFlow(draft, catalog, FILTER_A)

    expect(result.nodes.map((node) => node.id)).toEqual([INPUT_ID, FILTER_A, OUTPUT_ID])
    expect(result.edges.map((edge) => edge.id)).toEqual([
      '30000000-0000-4000-8000-000000000004',
      '30000000-0000-4000-8000-000000000005',
    ])
    expect(result.nodes[1].position).toEqual({ x: 320, y: 180 })
    expect(result.nodes[1].selected).toBe(true)

    result.nodes[1].position.x = 999
    expect(draft.definition.nodes[1].position.x).toBe(320)
  })
})

describe('changedPositionUpdates', () => {
  it('returns every moved node from a multi-selection drag', () => {
    const draft = draftFixture()
    const updates = changedPositionUpdates(draft, [
      { id: INPUT_ID, position: { x: 100, y: 220 } },
      { id: FILTER_A, position: { x: 360, y: 240 } },
      { id: OUTPUT_ID, position: { x: 560, y: 180 } },
    ])

    expect(updates).toEqual([
      { node_id: INPUT_ID, x: 100, y: 220 },
      { node_id: FILTER_A, x: 360, y: 240 },
    ])
  })
})
