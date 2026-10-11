import { describe, expect, it } from 'vitest'
import type { Changeset, ChangesetOp } from '../../../shared/types'
import { formatOpSummary, hasPendingOntologyChanges } from './changesetSummary'

function changeset(
  ops: Array<ChangesetOp['op']>,
  status: Changeset['status'] = 'open',
): Changeset {
  return {
    id: 'cs1',
    graph_id: 'g1',
    status,
    ops: ops.map((op, index) => ({
      id: `op${index}`,
      op,
      target_id: op === 'view' ? null : 'target',
      payload: {},
    })),
  }
}

describe('formatOpSummary', () => {
  it('counts only ontology ops, in a stable order', () => {
    expect(formatOpSummary(changeset(['delete', 'create', 'create', 'update']))).toEqual([
      '新增 2',
      '修改 1',
      '删除 1',
    ])
  })

  it('ignores view changes and empty changesets', () => {
    expect(formatOpSummary(changeset(['view', 'view']))).toEqual([])
    expect(formatOpSummary(changeset([]))).toEqual([])
    expect(formatOpSummary(null)).toEqual([])
  })
})

describe('hasPendingOntologyChanges', () => {
  it('is true only for an open changeset with ontology ops', () => {
    expect(hasPendingOntologyChanges(changeset(['create']))).toBe(true)
    expect(hasPendingOntologyChanges(changeset(['create'], 'confirmed'))).toBe(false)
    expect(hasPendingOntologyChanges(changeset(['view']))).toBe(false)
    expect(hasPendingOntologyChanges(changeset([]))).toBe(false)
    expect(hasPendingOntologyChanges(null)).toBe(false)
  })
})
