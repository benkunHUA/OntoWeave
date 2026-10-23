import type { Status } from './common'

export type PropertyDatatype =
  | 'text'
  | 'integer'
  | 'decimal'
  | 'boolean'
  | 'date'
  | 'timestamp'
  | 'enum'

export type OntologyResourceKind = 'object_type' | 'relation'

export interface OntologyProperty {
  id: string
  name: string
  datatype: PropertyDatatype
  required: boolean
  is_array: boolean
  is_primary_key: boolean
  is_title_key: boolean
  enum_values: string[] | null
  status: Status
}

export interface OntologyTypeDefinition {
  id: string
  name: string
  description: string | null
  status: Status
  properties: OntologyProperty[]
}

export interface OntologyResourceDefinition {
  id: string
  name: string
  kind: OntologyResourceKind
  status: Status
  description: string | null
  properties: OntologyProperty[]
  cardinality: string | null
  source_type_id: string | null
  source_type_name: string | null
  target_type_id: string | null
  target_type_name: string | null
  forward_label: string | null
  reverse_label: string | null
  foreign_key_property_id: string | null
  foreign_key_property: OntologyProperty | null
  source: OntologyTypeDefinition | null
  target: OntologyTypeDefinition | null
}
