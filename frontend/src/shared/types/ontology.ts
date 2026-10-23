import type { Status } from './common'
import type { OntologyResourceKind, PropertyDatatype } from './ontologyResource'

export type * from './ontologyResource'

export type DatasetKind = 'SYNC' | 'PIPELINE'
export type SubjectKind = 'OBJECT_TYPE' | 'RELATION'
export type RelationKind = 'FK' | 'MANY_TO_MANY'
export type RunStatus = 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED' | 'SUPERSEDED'
export type RunPhase = 'READING' | 'VALIDATING' | 'WRITING' | 'SWITCHING'
export type TriggerType = 'MANUAL' | 'DATASET_PUBLISHED' | 'RETRY'
export type ValidationState = 'OK' | 'NEEDS_REPAIR'
export type FilterOp = 'eq' | 'ne' | 'contains' | 'gt' | 'gte' | 'lt' | 'lte' | 'is_null' | 'is_not_null'

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
  version_number: number | null
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
  skipped: PreviewSkip[]
  skipped_count: number
  skipped_reasons: Record<string, number>
}

export interface PreviewSkip {
  code: string
  message: string
  reference: string | null
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
  skipped: PreviewSkip[]
}

export interface GenerateMappingRequest {
  mapping_revision: number
}

export interface GenerateMappingResponse {
  run_id: string
}

export interface BindingDraftRequest {
  base_revision: number
  dataset_kind: DatasetKind
  dataset_id: string
  mapping: ObjectMapping | RelationMapping
}

export interface BindingProbeRequest {
  dataset_kind?: DatasetKind
  dataset_id?: string
  mapping?: ObjectMapping | RelationMapping
}

export interface PreviewBindingRequest extends BindingProbeRequest {
  limit?: number
}

export interface MappingSuggestionReason {
  attribute_id: string | null
  column: string
  reason: string
  score: number
}

export interface MappingSuggestion {
  dataset_kind: DatasetKind
  dataset_id: string
  mapping: ObjectMapping | RelationMapping
  reasons: MappingSuggestionReason[]
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

export interface BindingRead {
  binding_id?: string | null
  id?: string | null
  object_type_id?: string | null
  relation_id?: string | null
  status?: string | null
  revision?: number | null
  draft_dataset_kind?: DatasetKind | null
  draft_dataset_id?: string | null
  draft_mapping?: ObjectMapping | RelationMapping | null
  active_dataset_kind?: DatasetKind | null
  active_dataset_id?: string | null
  active_dataset_version_id?: string | null
  active_mapping_revision?: number | null
  current_index_version_id?: string | null
  validation_state?: ValidationState | null
  validation_error?: Record<string, unknown> | null
  validation?: MappingValidation | null
  mapping?: ObjectMapping | RelationMapping | null
  recent_run?: IndexRun | null
  latest_run?: IndexRun | null
}

export interface OntologyResource {
  id: string
  name: string
  status: Status
  kind: OntologyResourceKind
  binding: BindingRead | null
  cardinality: string | null
  relation_kind: RelationKind | null
  source_type_id: string | null
  source_type_name: string | null
  target_type_id: string | null
  target_type_name: string | null
  forward_label: string | null
  reverse_label: string | null
  direction_label: string | null
  foreign_key_property_id: string | null
}

export interface OntologyResources {
  object_types: OntologyResource[]
  foreign_key_relations: OntologyResource[]
  many_to_many_relations: OntologyResource[]
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

export interface OntologyRelatedObject {
  primary_key: string
  title: string | null
}

export interface ObjectRelationGroup {
  relation_id: string
  direction: 'outgoing' | 'incoming' | 'both'
  direction_label: string | null
  label: string | null
  source_type_id: string | null
  source_type_name: string | null
  target_type_id: string | null
  target_type_name: string | null
  total: number
  items: OntologyRelatedObject[]
}

export interface ObjectRelationPage {
  items: OntologyObject[]
  total: number
  page: number
  page_size: number
}

export interface RelationOverview {
  relation_id: string
  relation_kind: RelationKind | null
  relation_name: string | null
  source_type_id: string | null
  target_type_id: string | null
  foreign_key_property_id: string | null
  warnings: MappingIssue[]
  latest_version: Record<string, unknown> | null
  latest_run: IndexRun | null
  source: Record<string, unknown> | null
  target: Record<string, unknown> | null
}

export interface FilterCondition {
  attribute_id: string
  op: FilterOp
  value?: unknown
}
