import { describe, expect, it } from 'vitest'
import type { GraphProperty } from '../../../shared/types'
import { assignmentValueSourceOptions } from './actionConfig'

function property(datatype: string, overrides: Partial<GraphProperty> = {}): GraphProperty {
  return {
    id: 'p1',
    name: '属性',
    datatype,
    required: false,
    is_array: false,
    is_primary_key: false,
    is_title_key: false,
    enum_values: null,
    status: 'confirmed',
    ...overrides,
  }
}

function values(propertyValue: GraphProperty | null, ruleKind: string): string[] {
  return assignmentValueSourceOptions(propertyValue, ruleKind).map((option) => option.value)
}

describe('assignmentValueSourceOptions', () => {
  it('only shows dynamic sources when the target supports them', () => {
    expect(values(null, 'create_object')).toEqual(['parameter', 'object_property', 'static'])
    expect(values(property('timestamp'), 'modify_object')).toContain('current_time')
    expect(values(property('integer'), 'modify_object')).not.toContain('current_time')
  })

  it('only shows auto generation for a create primary key', () => {
    const primaryKey = property('text', { is_primary_key: true })

    expect(values(primaryKey, 'create_object')).toContain('auto_generate')
    expect(values(primaryKey, 'modify_object')).not.toContain('auto_generate')
    expect(values(property('text'), 'create_object')).not.toContain('auto_generate')
  })
})
