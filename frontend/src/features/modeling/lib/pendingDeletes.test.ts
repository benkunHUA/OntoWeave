import { describe, expect, it } from 'vitest'
import type { Changeset } from '../../../shared/types'
import { pendingDeleteIds } from './pendingDeletes'

function changeset(ops: Changeset['ops']): Changeset {
  return { id: 'c1', graph_id: 'g1', status: 'open', ops }
}

describe('pendingDeleteIds', () => {
  it('collects deletes of the drawer-owned element kinds', () => {
    const ids = pendingDeleteIds(
      changeset([
        { id: 'o1', op: 'delete', target_id: 'p1', payload: { kind: 'property' } },
        { id: 'o2', op: 'delete', target_id: 'ap1', payload: { kind: 'action_parameter' } },
        { id: 'o3', op: 'delete', target_id: 'ar1', payload: { kind: 'action_rule' } },
        { id: 'o4', op: 'delete', target_id: 'ac1', payload: { kind: 'action_criteria_node' } },
      ]),
    )
    expect([...ids]).toEqual(['p1', 'ap1', 'ar1', 'ac1'])
  })

  it('ignores other kinds, updates and view ops', () => {
    const ids = pendingDeleteIds(
      changeset([
        { id: 'o1', op: 'delete', target_id: 'obj', payload: { kind: 'object_type' } },
        { id: 'o2', op: 'update', target_id: 'p2', payload: { kind: 'property' } },
        { id: 'o3', op: 'view', target_id: null, payload: {} },
        { id: 'o4', op: 'delete', target_id: null, payload: { kind: 'property' } },
      ]),
    )
    expect(ids.size).toBe(0)
  })

  it('returns nothing for a missing changeset', () => {
    expect(pendingDeleteIds(null).size).toBe(0)
  })
})
