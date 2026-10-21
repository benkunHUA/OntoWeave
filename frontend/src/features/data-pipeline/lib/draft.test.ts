import { describe, expect, it } from 'vitest'
import { normalizeDraftResponse, pipelineStateFromEvent } from './draft'
import { catalog, draftFixture } from './testFixtures'

describe('normalizeDraftResponse', () => {
  it('keeps the wrapped draft shape and guards definition arrays', () => {
    const draft = draftFixture()
    const normalized = normalizeDraftResponse(draft)

    expect(normalized.pipeline_id).toBe(draft.pipeline_id)
    expect(normalized.definition.schemaVersion).toBe(1)
    expect(normalized.definition.nodes).toHaveLength(3)
    expect(normalized.definition.nodes[0].position).toEqual({ x: 80, y: 180 })
    expect(normalized.definition.edges).toHaveLength(2)
  })
})

describe('pipelineStateFromEvent', () => {
  it('normalizes the Agent state update payload used by the canvas', () => {
    const state = pipelineStateFromEvent({
      v: 1,
      type: 'state.update',
      pipelineId: draftFixture().pipeline_id,
      draftRevision: 9,
      check: {
        pipeline_id: draftFixture().pipeline_id,
        revision: 9,
        stage: 'ready',
        blocking_issues: [],
        warnings: [],
        next_actions: [],
      },
      loadError: null,
    })

    expect(state?.draftRevision).toBe(9)
    expect(state?.check?.stage).toBe('ready')
    expect(catalog.nodes.length).toBeGreaterThan(0)
  })
})
