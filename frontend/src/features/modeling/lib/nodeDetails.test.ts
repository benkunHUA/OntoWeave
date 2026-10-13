import { describe, expect, it } from 'vitest'
import type { Changeset, GraphNode } from '../../../shared/types'
import {
  buildElementEdits,
  datatypeOptions,
  draftFromNode,
  draftValidationError,
  isDraftDirty,
  newPropertyDraft,
  pendingPropertyDeleteIds,
} from './nodeDetails'

function node(overrides: Partial<GraphNode> = {}): GraphNode {
  return {
    id: 'obj1',
    name: '客户',
    description: '购买商品的人',
    status: 'confirmed',
    properties: [
      {
        id: 'p1',
        name: '客户名',
        datatype: 'text',
        required: true,
        is_array: false,
        is_primary_key: false,
        is_title_key: false,
        status: 'confirmed',
      },
    ],
    ...overrides,
  }
}

describe('buildElementEdits', () => {
  it('emits nothing when the draft matches the node', () => {
    const source = node()
    expect(buildElementEdits(source, draftFromNode(source))).toEqual([])
  })

  it('sends only the changed object fields', () => {
    const source = node()
    const draft = { ...draftFromNode(source), name: '终端客户' }

    expect(buildElementEdits(source, draft)).toEqual([
      { op: 'update', element_kind: 'object_type', element_id: 'obj1', patch: { name: '终端客户' } },
    ])
  })

  it('normalises an emptied description to null', () => {
    const source = node()
    const draft = { ...draftFromNode(source), description: '   ' }

    expect(buildElementEdits(source, draft)).toEqual([
      {
        op: 'update',
        element_kind: 'object_type',
        element_id: 'obj1',
        patch: { description: null },
      },
    ])
  })

  it('emits a property update for a changed field name or type', () => {
    const source = node()
    const draft = draftFromNode(source)
    draft.properties[0] = { ...draft.properties[0], name: '客户全称', datatype: 'decimal' }

    expect(buildElementEdits(source, draft)).toEqual([
      {
        op: 'update',
        element_kind: 'property',
        element_id: 'p1',
        patch: { name: '客户全称', datatype: 'decimal' },
      },
    ])
  })

  it('emits a create_property op for a new field and trims its name', () => {
    const source = node()
    const draft = draftFromNode(source)
    draft.properties.push({ ...newPropertyDraft(), name: '  等级  ', datatype: 'integer' })

    expect(buildElementEdits(source, draft)).toEqual([
      {
        op: 'create_property',
        owner_type_id: 'obj1',
        name: '等级',
        datatype: 'integer',
        is_array: false,
        is_primary_key: false,
        is_title_key: false,
      },
    ])
  })

  it('carries key and array flags on property updates', () => {
    const source = node()
    const draft = draftFromNode(source)
    draft.properties[0] = {
      ...draft.properties[0],
      isArray: true,
      isPrimaryKey: true,
      isTitleKey: true,
    }

    expect(buildElementEdits(source, draft)).toEqual([
      {
        op: 'update',
        element_kind: 'property',
        element_id: 'p1',
        patch: { is_array: true, is_primary_key: true, is_title_key: true },
      },
    ])
  })

  it('emits a delete op for a removed existing field', () => {
    const source = node()
    const draft = { ...draftFromNode(source), properties: [], removedIds: ['p1'] }

    expect(buildElementEdits(source, draft)).toEqual([
      { op: 'delete', element_kind: 'property', element_id: 'p1' },
    ])
  })

  it('trims the object name before comparing', () => {
    const source = node()
    const draft = { ...draftFromNode(source), name: '  客户  ' }
    expect(buildElementEdits(source, draft)).toEqual([])
    expect(isDraftDirty(source, draft)).toBe(false)
  })
})

describe('draftValidationError', () => {
  it('rejects an empty object name or field name', () => {
    const source = node()
    expect(draftValidationError({ ...draftFromNode(source), name: '  ' })).toBe(
      '对象名称不能为空',
    )
    const draft = draftFromNode(source)
    draft.properties[0] = { ...draft.properties[0], name: '' }
    expect(draftValidationError(draft)).toBe('字段名不能为空')
  })

  it('blocks a brand new field that has no name yet', () => {
    const source = node()
    const draft = draftFromNode(source)
    draft.properties.push(newPropertyDraft())
    expect(draftValidationError(draft)).toBe('字段名不能为空')
  })

  it('passes a complete draft', () => {
    expect(draftValidationError(draftFromNode(node()))).toBeNull()
  })
})

describe('datatypeOptions', () => {
  it('keeps an unknown legacy datatype selectable', () => {
    const options = datatypeOptions('custom')
    expect(options.at(-1)).toEqual({ value: 'custom', label: 'custom' })
  })

  it('does not duplicate a known datatype', () => {
    expect(datatypeOptions('text')).not.toContainEqual({ value: 'text', label: 'text' })
  })
})

describe('pendingPropertyDeleteIds', () => {
  it('collects only property delete ops', () => {
    const changeset = {
      id: 'c1',
      graph_id: 'g1',
      status: 'open',
      ops: [
        { id: 'o1', op: 'delete', target_id: 'p1', payload: { kind: 'property' } },
        { id: 'o2', op: 'delete', target_id: 'obj', payload: { kind: 'object_type' } },
        { id: 'o3', op: 'update', target_id: 'p2', payload: { kind: 'property' } },
      ],
    } as Changeset

    expect([...pendingPropertyDeleteIds(changeset)]).toEqual(['p1'])
  })

  it('returns nothing for a missing changeset', () => {
    expect(pendingPropertyDeleteIds(null).size).toBe(0)
  })
})

describe('draftFromNode', () => {
  it('drops properties that are pending deletion', () => {
    const draft = draftFromNode(node(), new Set(['p1']))
    expect(draft.properties).toEqual([])
  })
})
