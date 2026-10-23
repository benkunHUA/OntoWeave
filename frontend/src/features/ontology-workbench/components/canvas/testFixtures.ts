import type {
  BindingRead,
  DatasetColumn,
  ObjectMapping,
  OntologyDataset,
  OntologyResource,
  OntologyResourceDefinition,
  RelationMapping,
} from '../../../../shared/types'

export const OBJECT_ID = 'object-1'
export const RELATION_ID = 'relation-1'
export const DATASET_ID = 'dataset-1'
export const ATTRIBUTE_ID = 'attribute-id'
export const NAME_ID = 'attribute-name'
export const AGE_ID = 'attribute-age'

export const objectDefinition: OntologyResourceDefinition = {
  id: OBJECT_ID,
  name: '客户',
  kind: 'object_type',
  status: 'confirmed',
  description: null,
  properties: [
    {
      id: ATTRIBUTE_ID, name: 'id', datatype: 'text', required: true,
      is_array: false, is_primary_key: true, is_title_key: false,
      enum_values: null, status: 'confirmed',
    },
    {
      id: NAME_ID, name: 'name', datatype: 'text', required: true,
      is_array: false, is_primary_key: false, is_title_key: true,
      enum_values: null, status: 'confirmed',
    },
    {
      id: AGE_ID, name: 'age', datatype: 'integer', required: false,
      is_array: false, is_primary_key: false, is_title_key: false,
      enum_values: null, status: 'confirmed',
    },
  ],
  cardinality: null,
  source_type_id: null,
  source_type_name: null,
  target_type_id: null,
  target_type_name: null,
  forward_label: null,
  reverse_label: null,
  foreign_key_property_id: null,
  foreign_key_property: null,
  source: null,
  target: null,
}

export const relationDefinition: OntologyResourceDefinition = {
  id: RELATION_ID,
  name: '客户订单',
  kind: 'relation',
  status: 'confirmed',
  description: null,
  properties: [],
  cardinality: 'many_to_many',
  source_type_id: 'customer-type',
  source_type_name: '客户',
  target_type_id: 'order-type',
  target_type_name: '订单',
  forward_label: '下订单',
  reverse_label: '属于',
  foreign_key_property_id: null,
  foreign_key_property: null,
  source: {
    id: 'customer-type', name: '客户', description: null, status: 'confirmed',
    properties: [{
      id: 'customer-id', name: 'id', datatype: 'text', required: true,
      is_array: false, is_primary_key: true, is_title_key: false,
      enum_values: null, status: 'confirmed',
    }],
  },
  target: {
    id: 'order-type', name: '订单', description: null, status: 'confirmed',
    properties: [{
      id: 'order-id', name: 'id', datatype: 'integer', required: true,
      is_array: false, is_primary_key: true, is_title_key: false,
      enum_values: null, status: 'confirmed',
    }],
  },
}

export const columns: DatasetColumn[] = [
  { name: 'customer_id', type: 'string', optional: false, example: 'c-1' },
  { name: 'customer_name', type: 'string', optional: false, example: 'Ada' },
  { name: 'age', type: 'int', optional: true, example: 36 },
  { name: 'order_id', type: 'long', optional: false, example: 1 },
  { name: 'score', type: 'double', optional: true, example: 1.5 },
]

export const dataset: OntologyDataset = {
  kind: 'PIPELINE',
  id: DATASET_ID,
  name: '客户宽表',
  version_id: 'version-1',
  version_number: 3,
  row_count: 120,
  columns,
}

export const emptyObjectMapping: ObjectMapping = {
  primary_key_attribute_id: ATTRIBUTE_ID,
  title_key_attribute_id: NAME_ID,
  attributes: [],
  required_attribute_ids: [ATTRIBUTE_ID, NAME_ID],
}

export const emptyRelationMapping: RelationMapping = {
  from_key_column: '',
  from_key_datatype: 'text',
  to_key_column: '',
  to_key_datatype: 'integer',
}

export function objectBinding(revision = 1, mapping = emptyObjectMapping): BindingRead {
  return {
    binding_id: 'binding-1',
    revision,
    draft_dataset_kind: 'PIPELINE',
    draft_dataset_id: DATASET_ID,
    draft_mapping: mapping,
  }
}

export function resource(kind: 'object' | 'relation' = 'object'): OntologyResource {
  return {
    id: kind === 'object' ? OBJECT_ID : RELATION_ID,
    name: kind === 'object' ? '客户' : '客户订单',
    status: 'confirmed',
    kind: kind === 'object' ? 'object_type' : 'relation',
    binding: null,
    cardinality: kind === 'relation' ? 'many_to_many' : null,
    relation_kind: kind === 'relation' ? 'MANY_TO_MANY' : null,
    source_type_id: kind === 'relation' ? 'customer-type' : null,
    source_type_name: kind === 'relation' ? '客户' : null,
    target_type_id: kind === 'relation' ? 'order-type' : null,
    target_type_name: kind === 'relation' ? '订单' : null,
    forward_label: kind === 'relation' ? '下订单' : null,
    reverse_label: kind === 'relation' ? '属于' : null,
    direction_label: null,
    foreign_key_property_id: null,
  }
}
