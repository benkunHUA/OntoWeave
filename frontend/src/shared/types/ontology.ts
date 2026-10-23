import type { Status } from './common'

export interface GraphProperty {
  id: string
  name: string
  datatype: string
  required: boolean
  is_array: boolean
  is_primary_key: boolean
  is_title_key: boolean
  /** datatype = enum 时的候选值；其余类型为 null。 */
  enum_values: string[] | null
  status: Status
}

export interface GraphNode {
  id: string
  name: string
  description: string | null
  status: Status
  properties: GraphProperty[]
}

export interface GraphRelation {
  id: string
  name: string
  source_type_id: string
  target_type_id: string
  direction: string
  cardinality: string
  forward_label: string | null
  reverse_label: string | null
  /** 一对一 / 一对多：承载这条链接的外键属性 id（存对端主键）；多对多为 null。 */
  foreign_key_property_id: string | null
  status: Status
}

export type PropertyDatatype =
  | 'text'
  | 'integer'
  | 'decimal'
  | 'boolean'
  | 'date'
  | 'timestamp'
  | 'enum'

export type DatasetKind = 'SYNC' | 'PIPELINE'
export type SubjectKind = 'OBJECT_TYPE' | 'RELATION'
export type RelationKind = 'FK' | 'MANY_TO_MANY'
export type RunStatus =
  | 'QUEUED'
  | 'RUNNING'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'CANCELLED'
  | 'SUPERSEDED'
export type RunPhase = 'READING' | 'VALIDATING' | 'WRITING' | 'SWITCHING'
export type TriggerType = 'MANUAL' | 'DATASET_PUBLISHED' | 'RETRY'
export type ValidationState = 'OK' | 'NEEDS_REPAIR'
export type FilterOp =
  | 'eq'
  | 'ne'
  | 'contains'
  | 'gt'
  | 'gte'
  | 'lt'
  | 'lte'
  | 'is_null'
  | 'is_not_null'

export interface DatasetColumn {
  name: string
  type: string
  optional: boolean
  example: unknown | null
}

export interface OntologyDataset {
  kind: DatasetKind
  id: string
  name: string
  version_id: string
  version_number: number
  row_count: number
  columns: DatasetColumn[]
}

export interface OntologyDatasetPage {
  items: OntologyDataset[]
  total: number
  page: number
  size: number
}

export interface AttributeMapping {
  attribute_id: string
  column: string
  datatype: PropertyDatatype
  required: boolean
  is_array: boolean
  enum_values: string[] | null
}

export interface ObjectMapping {
  primary_key_attribute_id: string
  title_key_attribute_id: string | null
  attributes: AttributeMapping[]
}

export interface RelationMapping {
  from_key_column: string
  from_key_datatype: PropertyDatatype
  to_key_column: string
  to_key_datatype: PropertyDatatype
}

export interface MappingIssue {
  code: string
  message: string
  attribute_id: string | null
  column: string | null
}

export interface MappingValidation {
  blocking: MappingIssue[]
  warnings: MappingIssue[]
  valid: boolean
}

export interface ObjectPreviewRow {
  primary_key: string
  title: string | null
  properties: Record<string, unknown>
}

export interface ObjectPreview {
  rows: ObjectPreviewRow[]
  validation: MappingValidation
}

export interface RelationPreviewPair {
  from_primary_key: string
  to_primary_key: string
}

export interface RelationPreview {
  pairs: RelationPreviewPair[]
  skipped_count: number
  skipped_reasons: Record<string, number>
  validation: MappingValidation
}

export interface MappingUpdateRequest {
  base_revision: number
  mapping: ObjectMapping | RelationMapping
}

export interface GenerateMappingRequest {
  mapping_revision: number
}

export interface GenerateMappingResponse {
  run_id: string
}

export interface IndexRun {
  id: string
  owner_id: string
  subject_kind: SubjectKind
  subject_id: string
  binding_id: string | null
  trigger_type: TriggerType
  status: RunStatus
  phase: RunPhase | null
  target_dataset_kind: DatasetKind
  target_dataset_version_id: string
  mapping_revision: number
  requested_at: string
  started_at: string | null
  heartbeat_at: string | null
  finished_at: string | null
  error_code: string | null
  error_message: string | null
  stats: Record<string, unknown>
}

export interface OntologyObject {
  primary_key: string
  title: string | null
  index_version_id: string
}

export interface OntologyObjectPage {
  items: OntologyObject[]
  total: number
  page: number
  page_size: number
  index_version_id: string | null
}

export interface OntologyObjectDetail {
  primary_key: string
  title: string | null
  properties: Record<string, unknown>
  index_version_id: string
}

export interface RelationInstance {
  from_object_type_id: string
  from_primary_key: string
  to_object_type_id: string
  to_primary_key: string
  index_version_id: string | null
}

export interface RelationInstancePage {
  items: RelationInstance[]
  total: number
  page: number
  page_size: number
}

export interface FilterCondition {
  attribute_id: string
  op: FilterOp
  value?: unknown
}
