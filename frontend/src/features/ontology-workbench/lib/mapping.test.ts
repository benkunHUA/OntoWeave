import { describe, expect, it } from 'vitest'
import type { MappingSuggestion, ObjectMapping } from '../../../shared/types'
import {
  AGE_ID,
  ATTRIBUTE_ID,
  NAME_ID,
  columns,
  emptyObjectMapping,
  emptyRelationMapping,
  objectDefinition,
  relationDefinition,
} from '../components/canvas/testFixtures'
import {
  connectObjectAttribute,
  connectRelationEndpoint,
  isDatasetTypeCompatible,
  localObjectIssues,
  localRelationIssues,
  mergeObjectSuggestion,
  removeObjectConnection,
  sanitizeObjectMapping,
} from './mapping'

const attribute = objectDefinition.properties[0]!
const name = objectDefinition.properties[1]!
const age = objectDefinition.properties[2]!
const customerId = columns[0]!
const customerName = columns[1]!
const ageColumn = columns[2]!
const orderId = columns[3]!
const score = columns[4]!

function objectMapping(): ObjectMapping {
  return { ...emptyObjectMapping, attributes: [] }
}

describe('dataset type matrix', () => {
  it('accepts the documented compatible types', () => {
    expect(isDatasetTypeCompatible('text', 'string')).toBe(true)
    expect(isDatasetTypeCompatible('text', 'uuid')).toBe(true)
    expect(isDatasetTypeCompatible('integer', 'long')).toBe(true)
    expect(isDatasetTypeCompatible('decimal', 'int')).toBe(true)
    expect(isDatasetTypeCompatible('timestamp', 'timestamp with time zone')).toBe(true)
    expect(isDatasetTypeCompatible('timestamp', 'timestamptz(6)')).toBe(true)
  })

  it('rejects lossy and unsupported conversions', () => {
    expect(isDatasetTypeCompatible('integer', 'double')).toBe(false)
    expect(isDatasetTypeCompatible('decimal', 'double')).toBe(false)
    expect(isDatasetTypeCompatible('date', 'timestamp')).toBe(false)
    expect(isDatasetTypeCompatible('text', 'int')).toBe(false)
    expect(isDatasetTypeCompatible('integer', 'list<int>')).toBe(false)
  })
})

describe('object mapping constraints', () => {
  it('allows one edge per attribute and per column', () => {
    const first = connectObjectAttribute(objectMapping(), attribute, customerId)
    expect(first.ok).toBe(true)
    if (!first.ok) return

    expect(connectObjectAttribute(first.mapping as ObjectMapping, attribute, customerName)).toMatchObject({
      ok: false,
      code: 'ATTRIBUTE_OCCUPIED',
    })
    expect(connectObjectAttribute(first.mapping as ObjectMapping, name, customerId)).toMatchObject({
      ok: false,
      code: 'COLUMN_OCCUPIED',
    })
    expect(connectObjectAttribute(first.mapping as ObjectMapping, name, customerName).ok).toBe(true)
  })

  it('rejects incompatible and lossy types with a reason', () => {
    expect(connectObjectAttribute(objectMapping(), age, score)).toMatchObject({
      ok: false,
      code: 'TYPE_INCOMPATIBLE',
    })
    expect(connectObjectAttribute(objectMapping(), age, ageColumn).ok).toBe(true)
  })

  it('reports required and primary key attributes before generation', () => {
    const result = localObjectIssues(objectMapping(), objectDefinition, columns)

    expect(result.blocking.map((item) => item.code)).toEqual(expect.arrayContaining([
      'PRIMARY_KEY_NOT_MAPPED',
      'REQUIRED_ATTRIBUTE_UNMAPPED',
    ]))
    expect(result.warnings.map((item) => item.code)).toContain('TITLE_FALLBACK')
  })

  it('removes an edge by its stable id', () => {
    const connected = connectObjectAttribute(objectMapping(), attribute, customerId)
    expect(connected.ok).toBe(true)
    if (!connected.ok) return
    const removed = removeObjectConnection(connected.mapping as ObjectMapping, `object:${ATTRIBUTE_ID}`)
    expect(removed.attributes).toEqual([])
  })

  it('sanitizes duplicate attributes and columns and refreshes required ids', () => {
    const mapping = sanitizeObjectMapping({
      ...emptyObjectMapping,
      attributes: [
        { attribute_id: ATTRIBUTE_ID, column: 'customer_id', datatype: 'text', required: true, is_array: false, enum_values: null },
        { attribute_id: NAME_ID, column: 'customer_id', datatype: 'text', required: true, is_array: false, enum_values: null },
        { attribute_id: AGE_ID, column: 'age', datatype: 'integer', required: false, is_array: false, enum_values: null },
        { attribute_id: AGE_ID, column: 'age', datatype: 'integer', required: false, is_array: false, enum_values: null },
      ],
    }, objectDefinition)

    expect(mapping.attributes).toHaveLength(2)
    expect(mapping.required_attribute_ids).toEqual([ATTRIBUTE_ID, NAME_ID])
  })

  it('only fills empty compatible suggestion edges', () => {
    const connected = connectObjectAttribute(objectMapping(), attribute, customerId)
    expect(connected.ok).toBe(true)
    if (!connected.ok) return
    const suggestion: MappingSuggestion = {
      dataset_kind: 'PIPELINE',
      dataset_id: 'dataset-1',
      mapping: {
        ...emptyObjectMapping,
        attributes: [
          { attribute_id: ATTRIBUTE_ID, column: 'customer_id', datatype: 'text', required: true, is_array: false, enum_values: null },
          { attribute_id: NAME_ID, column: 'customer_name', datatype: 'text', required: true, is_array: false, enum_values: null },
          { attribute_id: AGE_ID, column: 'score', datatype: 'integer', required: false, is_array: false, enum_values: null },
        ],
      },
      reasons: [],
    }

    const merged = mergeObjectSuggestion(connected.mapping as ObjectMapping, suggestion, objectDefinition, columns)

    expect(merged.added).toBe(1)
    expect((merged.mapping as ObjectMapping).attributes.map((item) => item.column)).toEqual([
      'customer_id', 'customer_name',
    ])
  })
})

describe('many-to-many mapping constraints', () => {
  it('maps exactly two endpoint edges to distinct compatible columns', () => {
    const from = connectRelationEndpoint(emptyRelationMapping, 'from', 'text', customerId)
    expect(from.ok).toBe(true)
    if (!from.ok) return

    expect(connectRelationEndpoint(from.mapping as typeof emptyRelationMapping, 'to', 'integer', customerId)).toMatchObject({
      ok: false,
      code: 'COLUMN_OCCUPIED',
    })
    expect(connectRelationEndpoint(from.mapping as typeof emptyRelationMapping, 'to', 'integer', customerName)).toMatchObject({
      ok: false,
      code: 'TYPE_INCOMPATIBLE',
    })
    const to = connectRelationEndpoint(from.mapping as typeof emptyRelationMapping, 'to', 'integer', orderId)
    expect(to.ok).toBe(true)
    if (!to.ok) return

    expect(to.mapping).toMatchObject({
      from_key_column: 'customer_id',
      to_key_column: 'order_id',
    })
    expect(localRelationIssues(to.mapping as typeof emptyRelationMapping, relationDefinition, columns).blocking).toEqual([])
  })

  it('rejects a second edge from the same endpoint', () => {
    const from = connectRelationEndpoint(emptyRelationMapping, 'from', 'text', customerId)
    expect(from.ok).toBe(true)
    if (!from.ok) return
    expect(connectRelationEndpoint(from.mapping as typeof emptyRelationMapping, 'from', 'text', customerName)).toMatchObject({
      ok: false,
      code: 'ENDPOINT_OCCUPIED',
    })
  })
})
