import { describe, expect, it } from 'vitest'
import type { GraphRelation } from '../../../shared/types'
import {
  buildRelationEdits,
  cardinalityLabel,
  cardinalityMark,
  deleteRelationEdit,
  draftFromRelation,
  isRelationDraftDirty,
  relationDraftValidationError,
  relationEdgeLabel,
} from './relationDetails'

describe('relationEdgeLabel', () => {
  it('prefers the forward label', () => {
    expect(relationEdgeLabel({ name: '下订单', forward_label: '订单' })).toBe('订单')
  })

  it('falls back to the relation name when the label is blank', () => {
    expect(relationEdgeLabel({ name: '下订单', forward_label: '  ' })).toBe('下订单')
    expect(relationEdgeLabel({ name: '下订单', forward_label: null })).toBe('下订单')
  })
})

describe('cardinality helpers', () => {
  it('maps known cardinalities to label and mark', () => {
    expect(cardinalityLabel('one_to_many')).toBe('一对多')
    expect(cardinalityMark('one_to_many')).toBe('1:N')
  })

  it('passes through unknown values', () => {
    expect(cardinalityMark('weird')).toBe('weird')
  })
})

function relation(): GraphRelation {
  return {
    id: 'r1',
    name: '下订单',
    source_type_id: 'obj1',
    target_type_id: 'obj2',
    direction: 'forward',
    cardinality: 'many_to_many',
    forward_label: null,
    reverse_label: null,
    status: 'confirmed',
  }
}

describe('relation drafts', () => {
  it('round-trips an unchanged relation to no edits', () => {
    const source = relation()
    expect(buildRelationEdits(source, draftFromRelation(source))).toEqual([])
    expect(isRelationDraftDirty(source, draftFromRelation(source))).toBe(false)
  })

  it('emits only the changed fields, normalising blank labels to null', () => {
    const source = relation()
    const draft = {
      ...draftFromRelation(source),
      cardinality: 'one_to_many',
      forwardLabel: '订单',
      reverseLabel: '   ',
    }

    expect(buildRelationEdits(source, draft)).toEqual([
      {
        op: 'update',
        element_kind: 'relation',
        element_id: 'r1',
        patch: { cardinality: 'one_to_many', forward_label: '订单' },
      },
    ])
  })

  it('clears a label that was emptied out', () => {
    const source = { ...relation(), forward_label: '订单' }
    const draft = { ...draftFromRelation(source), forwardLabel: '' }
    expect(buildRelationEdits(source, draft)).toEqual([
      {
        op: 'update',
        element_kind: 'relation',
        element_id: 'r1',
        patch: { forward_label: null },
      },
    ])
  })

  it('validates the relation name', () => {
    expect(relationDraftValidationError({ ...draftFromRelation(relation()), name: '  ' })).toBe(
      '关系名不能为空',
    )
    expect(relationDraftValidationError(draftFromRelation(relation()))).toBeNull()
  })

  it('builds a delete op for the relation', () => {
    expect(deleteRelationEdit('r1')).toEqual({
      op: 'delete',
      element_kind: 'relation',
      element_id: 'r1',
    })
  })
})
