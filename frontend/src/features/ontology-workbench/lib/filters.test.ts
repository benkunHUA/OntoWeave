import { describe, expect, it } from 'vitest'
import type { OntologyProperty } from '../../../shared/types'
import {
  buildFilterConditions,
  createFilterDraft,
  operatorsFor,
  parseFilterValue,
} from './filters'

function property(overrides: Partial<OntologyProperty> = {}): OntologyProperty {
  return {
    id: 'attribute-1',
    name: '客户名称',
    datatype: 'text',
    required: false,
    is_array: false,
    is_primary_key: false,
    is_title_key: false,
    enum_values: null,
    status: 'confirmed',
    ...overrides,
  }
}

describe('ontology filter rules', () => {
  it('narrows operators by datatype', () => {
    expect(operatorsFor('boolean').map((item) => item.value)).toEqual(['eq'])
    expect(operatorsFor('integer').map((item) => item.value)).toEqual([
      'eq', 'ne', 'gt', 'gte', 'lt', 'lte', 'is_null', 'is_not_null',
    ])
  })

  it('builds AND conditions and omits values for unary operators', () => {
    const name = property()
    const age = property({ id: 'attribute-2', name: '年龄', datatype: 'integer' })
    const nameDraft = { ...createFilterDraft(name), op: 'contains' as const, value: 'Ada' }
    const ageDraft = { ...createFilterDraft(age), op: 'gte' as const, value: '18' }
    const emptyAgeDraft = { ...createFilterDraft(age), op: 'is_null' as const, value: 'ignored' }

    expect(buildFilterConditions([nameDraft, ageDraft, emptyAgeDraft], [name, age])).toEqual({
      valid: true,
      conditions: [
        { attribute_id: 'attribute-1', op: 'contains', value: 'Ada' },
        { attribute_id: 'attribute-2', op: 'gte', value: 18 },
        { attribute_id: 'attribute-2', op: 'is_null' },
      ],
    })
  })

  it('rejects values that do not match the property type', () => {
    const age = property({ id: 'attribute-2', datatype: 'integer' })
    const draft = { ...createFilterDraft(age), op: 'eq' as const, value: '18.5' }

    expect(buildFilterConditions([draft], [age])).toMatchObject({ valid: false, draftId: draft.id })
    expect(parseFilterValue(property({ datatype: 'boolean' }), 'eq', 'true')).toBe(true)
    expect(parseFilterValue(property({ datatype: 'date' }), 'eq', '2026-10-24')).toBe('2026-10-24')
  })

  it('rejects operators that are not valid for the current datatype', () => {
    const flag = property({ datatype: 'boolean' })
    const draft = { ...createFilterDraft(flag), op: 'contains' as const, value: 'true' }

    expect(buildFilterConditions([draft], [flag]).valid).toBe(false)
  })
})
