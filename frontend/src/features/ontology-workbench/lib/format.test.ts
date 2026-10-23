import { describe, expect, it } from 'vitest'
import type { OntologyResource } from '../../../shared/types'
import { formatResourceRef, parseResourceRef, resourceCategory, resourceTabs } from './format'

function resource(overrides: Partial<OntologyResource> = {}): OntologyResource {
  return {
    id: 'resource-1',
    name: '客户',
    status: 'confirmed',
    kind: 'object_type',
    binding: null,
    cardinality: null,
    relation_kind: null,
    source_type_id: null,
    source_type_name: null,
    target_type_id: null,
    target_type_name: null,
    forward_label: null,
    reverse_label: null,
    direction_label: null,
    foreign_key_property_id: null,
    ...overrides,
  }
}

describe('ontology resource references', () => {
  it('round-trips object and relation deep links', () => {
    const objectRef = { kind: 'object_type' as const, id: 'object-1' }
    const relationRef = { kind: 'relation' as const, id: 'relation-1' }

    expect(parseResourceRef('object_type:object-1')).toEqual(objectRef)
    expect(parseResourceRef('relation:relation-1')).toEqual(relationRef)
    expect(formatResourceRef({ kind: 'object_type', id: 'object-1' })).toBe('object_type:object-1')
  })

  it('rejects malformed or over-segmented values', () => {
    expect(parseResourceRef('')).toBeNull()
    expect(parseResourceRef('object-1')).toBeNull()
    expect(parseResourceRef('dataset:object-1')).toBeNull()
    expect(parseResourceRef('object_type:')).toBeNull()
    expect(parseResourceRef('object_type:one:two')).toBeNull()
  })

  it('selects tabs by object, foreign-key and many-to-many categories', () => {
    const object = resource()
    const foreignKey = resource({ kind: 'relation', relation_kind: 'FK' })
    const manyToMany = resource({ kind: 'relation', relation_kind: 'MANY_TO_MANY' })

    expect(resourceCategory(object)).toBe('object_type')
    expect(resourceTabs(object).map((tab) => tab.label)).toEqual(['对象映射', '对象数据', '运行记录'])
    expect(resourceCategory(foreignKey)).toBe('foreign_key')
    expect(resourceTabs(foreignKey).map((tab) => tab.label)).toEqual([
      '关系概览',
      '关系实例',
      '运行记录',
    ])
    expect(resourceCategory(manyToMany)).toBe('many_to_many')
    expect(resourceTabs(manyToMany).map((tab) => tab.label)).toEqual([
      '关系映射',
      '关系实例',
      '运行记录',
    ])
  })
})
