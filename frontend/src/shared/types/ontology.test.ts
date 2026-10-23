import { describe, expect, it } from 'vitest'

import type {
  FilterCondition,
  IndexRun,
  MappingValidation,
  ObjectMapping,
  OntologyDataset,
  OntologyObject,
  RelationInstance,
  RelationMapping,
} from './ontology'

describe('ontology shared contract', () => {
  it('constructs bindable dataset, mapping and validation samples', () => {
    const dataset: OntologyDataset = {
      kind: 'PIPELINE',
      id: 'dataset-1',
      name: 'orders',
      version_id: 'version-1',
      version_number: 3,
      row_count: 42,
      columns: [{ name: 'order_id', type: 'long', optional: false, example: 1 }],
    }
    const mapping: ObjectMapping = {
      primary_key_attribute_id: 'attribute-1',
      title_key_attribute_id: null,
      attributes: [
        {
          attribute_id: 'attribute-1',
          column: 'order_id',
          datatype: 'integer',
          required: true,
          is_array: false,
          enum_values: null,
        },
      ],
    }
    const validation: MappingValidation = {
      blocking: [],
      warnings: [
        {
          code: 'TITLE_FALLBACK',
          message: '标题为空时回退主键',
          attribute_id: null,
          column: null,
        },
      ],
      valid: true,
    }

    expect(dataset.kind).toBe('PIPELINE')
    expect(mapping.attributes[0]?.column).toBe('order_id')
    expect(validation.valid).toBe(true)
  })

  it('constructs relation, filter, run and object samples', () => {
    const relation: RelationMapping = {
      from_key_column: 'customer_id',
      from_key_datatype: 'text',
      to_key_column: 'order_id',
      to_key_datatype: 'integer',
    }
    const filter: FilterCondition = {
      attribute_id: 'attribute-2',
      op: 'gte',
      value: 100,
    }
    const run: IndexRun = {
      id: 'run-1',
      owner_id: 'owner-1',
      subject_kind: 'OBJECT_TYPE',
      subject_id: 'object-type-1',
      binding_id: 'binding-1',
      trigger_type: 'MANUAL',
      status: 'QUEUED',
      phase: null,
      target_dataset_kind: 'PIPELINE',
      target_dataset_version_id: 'version-1',
      mapping_revision: 2,
      requested_at: '2026-10-23T00:00:00Z',
      started_at: null,
      heartbeat_at: null,
      finished_at: null,
      error_code: null,
      error_message: null,
      stats: {},
    }
    const object: OntologyObject = {
      primary_key: '42',
      title: 'Order 42',
      index_version_id: 'index-1',
    }
    const instance: RelationInstance = {
      from_object_type_id: 'customer-type',
      from_primary_key: 'c-1',
      to_object_type_id: 'order-type',
      to_primary_key: '42',
      index_version_id: 'relation-index-1',
    }

    expect(relation.to_key_datatype).toBe('integer')
    expect(filter.op).toBe('gte')
    expect(run.status).toBe('QUEUED')
    expect(object.primary_key).toBe('42')
    expect(instance.to_primary_key).toBe('42')
  })
})
