import { describe, expect, it } from 'vitest'
import type { PipelineDraftResponse, PipelineNode } from '../../../shared/types'
import {
  applyDraftResponse,
  drawerStateFor,
  resetForNode,
  syncExternalConfig,
} from './nodeConfigState'

const nodeId = '00000000-0000-4000-8000-000000000001'

const unboundNode: PipelineNode = {
  id: nodeId,
  type: 'output',
  name: 'Orders',
  config: {},
  position: { x: 0, y: 0 },
}

const boundDraft: PipelineDraftResponse = {
  pipeline_id: '00000000-0000-4000-8000-000000000010',
  revision: 9,
  definition: {
    schemaVersion: 1,
    nodes: [{
      ...unboundNode,
      config: { outputId: '00000000-0000-4000-8000-000000000099' },
    }],
    edges: [],
  },
}

describe('node config drawer state', () => {
  it('keeps outputId through set_output and a later name save', () => {
    let state = drawerStateFor(unboundNode)
    expect(state.config).toEqual({})

    state = { ...state, name: 'Orders draft', dirty: true }
    state = applyDraftResponse(state, nodeId, boundDraft)

    expect(state.config.outputId).toBe('00000000-0000-4000-8000-000000000099')
    expect(state.dirty).toBe(true)

    state = { ...state, name: 'Orders renamed', dirty: true }
    expect(state.config).toEqual({
      outputId: '00000000-0000-4000-8000-000000000099',
    })
  })

  it('does not overwrite an externally updated config while dirty', () => {
    const state = { ...drawerStateFor(unboundNode), dirty: true }
    const next = syncExternalConfig(state, boundDraft.definition.nodes[0])

    expect(next).toBe(state)
    expect(next.config).toEqual({})
  })

  it('synchronizes external config when the drawer is clean', () => {
    const next = syncExternalConfig(drawerStateFor(unboundNode), boundDraft.definition.nodes[0])

    expect(next.config.outputId).toBe('00000000-0000-4000-8000-000000000099')
  })

  it('synchronizes an external Agent rename only when the drawer is clean', () => {
    const renamed = { ...boundDraft.definition.nodes[0], name: 'Agent renamed' }
    const clean = syncExternalConfig(drawerStateFor(unboundNode), renamed)
    const dirty = syncExternalConfig(
      { ...drawerStateFor(unboundNode), name: 'Local rename', dirty: true },
      renamed,
    )

    expect(clean.name).toBe('Agent renamed')
    expect(dirty.name).toBe('Local rename')
  })

  it('keeps a clean drawer clean when set_output returns', () => {
    const state = applyDraftResponse(drawerStateFor(unboundNode), nodeId, boundDraft)
    expect(state.dirty).toBe(false)
  })

  it('resets local state when switching nodes', () => {
    const dirty = { ...drawerStateFor(unboundNode), name: 'Unsaved', dirty: true }
    const other: PipelineNode = {
      id: '00000000-0000-4000-8000-000000000002',
      type: 'filter',
      name: 'Filter',
      config: { condition: { kind: 'compare', field: 'id', op: 'is_not_null' } },
      position: { x: 100, y: 0 },
    }

    const next = resetForNode(dirty, other)

    expect(next.nodeId).toBe(other.id)
    expect(next.name).toBe('Filter')
    expect(next.config).toEqual(other.config)
    expect(next.dirty).toBe(false)
  })
})
