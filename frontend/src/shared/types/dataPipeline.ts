export type JsonObject = Record<string, unknown>

export interface Pipeline {
  id: string
  conversation_id: string
  name: string
  status: string
  draft_revision: number | null
  current_version_id: string | null
  created_at: string | null
  updated_at: string | null
}

export interface PipelinePage {
  items: Pipeline[]
  total: number
  limit: number
  offset: number
}

export interface PipelineCreate {
  name: string
}

export interface PipelineUpdate {
  name: string
}

export interface PipelineCopy {
  name?: string
}

export interface PipelineNodePosition {
  x: number
  y: number
}

export interface PipelineNode {
  id: string
  type: string
  name: string
  config: JsonObject
  position: PipelineNodePosition
}

export interface PipelineEdge {
  id: string
  sourceNodeId: string
  sourcePort: string
  targetNodeId: string
  targetPort: string
}

/** Java 草稿 JSON 固定使用 camelCase；外层浏览器响应仍是 snake_case。 */
export interface PipelineDefinition {
  schemaVersion: number
  nodes: PipelineNode[]
  edges: PipelineEdge[]
}

export interface PipelineDraftResponse {
  pipeline_id: string
  revision: number
  definition: PipelineDefinition
}

export interface PipelineNodePort {
  id: string
  role: 'input' | 'output'
  data_type: string
  required: boolean
  multiple: boolean
}

export interface ConfigSchema {
  type?: string | string[]
  enum?: unknown[]
  items?: ConfigSchema
  properties?: Record<string, ConfigSchema>
  required?: string[]
  [key: string]: unknown
}

export interface NodeDefinition {
  type: string
  version: number
  label: string
  category: string
  ports: PipelineNodePort[]
  config_schema: ConfigSchema
  ui_schema: { fields: Record<string, { widget: string }> }
}

export interface NodeCatalog {
  catalog_version: string
  nodes: NodeDefinition[]
}

export interface PipelineIssue {
  code: string
  phase: string | null
  blocking: boolean
  node_id: string | null
  port_id: string | null
  field: string | null
  message: string
  hint: string | null
}

export interface PipelineNextAction {
  action: string
  node_id: string | null
}

export interface PipelineCheckResult {
  pipeline_id: string
  revision: number
  stage: string
  blocking_issues: PipelineIssue[]
  warnings: PipelineIssue[]
  next_actions: PipelineNextAction[]
}

export interface PipelineDraftCommand {
  type: string
  base_revision: number
  operation_id: string
  turn_id?: string
  node?: JsonObject
  edge?: JsonObject
  output?: JsonObject
}

export interface PipelinePreviewRequest {
  draft_revision: number
  node_id: string
  request_id: string
}

export interface PipelineBuildRequest {
  draft_revision: number
  operation_id: string
}

export interface PipelineSchedulePutRequest {
  name: string
  cron_expression: string
  timezone: string
}

export interface PipelineNodePositionUpdate {
  node_id: string
  x: number
  y: number
}

export interface PipelinePreviewColumn {
  name: string
  type: string
  nullable: boolean
}

export interface PipelinePreviewIssue {
  code: string | null
  phase: string | null
  level: string | null
  node_id: string | null
  port_id: string | null
  field: string | null
  row_number: number | null
  message: string | null
  hint: string | null
  original_value: string | null
}

export interface PipelineInputVersion {
  dataset_kind: string
  dataset_id: string
  dataset_version_id: string
  dataset_version_number: number
  schema_hash: string
  iceberg_catalog: string | null
  iceberg_namespace: string | null
  iceberg_table: string | null
  iceberg_snapshot_id: number | null
  display_snapshot: JsonObject | null
}

export interface PipelinePreview {
  preview_id: string
  pipeline_id: string
  draft_revision: number
  node_id: string
  columns: PipelinePreviewColumn[]
  rows: JsonObject[]
  returned_rows: number
  scanned_rows: number | null
  truncated: boolean
  truncation_reason: string | null
  elapsed_ms: number
  input_versions: PipelineInputVersion[]
  errors: PipelinePreviewIssue[]
  warnings: PipelinePreviewIssue[]
}

export interface PipelineBuildOutput {
  id: string
  output_id: string
  output_type: string
  status: string
  stage: string | null
  engine_job_id: string | null
  error_code: string | null
  error_message: string | null
  result: JsonObject | null
  stats: JsonObject | null
  started_at: string | null
  finished_at: string | null
}

export interface PipelineBuild {
  id: string
  pipeline_id: string
  pipeline_version_id: string | null
  status: string
  stage: string | null
  trigger: string
  error_code: string | null
  error_message: string | null
  started_at: string | null
  finished_at: string | null
  outputs: PipelineBuildOutput[]
}

export interface InputDatasetVersion {
  id: string
  version_number: number
  row_count: number
  column_count: number
  schema: unknown
  published_at: string | null
}

export interface InputDataset {
  id: string
  name: string
  connection_id: string
  sync_task_id: string
  current_version_id: string | null
  current_version: InputDatasetVersion | null
  source_schema: string | null
  source_table: string | null
  updated_at: string | null
}

export interface InputDatasetPage {
  items: InputDataset[]
  total: number
  limit: number
  offset: number
}

export interface DatasetSchema {
  dataset_id: string
  version_id: string | null
  version_number: number | null
  schema: unknown
  column_count: number
}

export interface PipelineSource {
  pipeline_id: string
  dataset_id: string
  version_id: string | null
  output: JsonObject
  build: JsonObject | null
  pipeline_version: JsonObject | null
  inputs: JsonObject[]
}

export interface PipelineSchedule {
  id: string
  pipeline_id: string
  name: string
  status: string
  trigger_type: string
  cron_expression: string
  timezone: string
  next_run_at: string | null
  last_run_at: string | null
  paused_at: string | null
}

export interface PipelineScheduleRun {
  id: string
  schedule_id: string
  pipeline_id: string
  status: string
  trigger_type: string | null
  scheduled_for: string
  build_id: string | null
  logic_hash: string | null
  reason_code: string | null
  message: string | null
}

export interface PipelineAgentState {
  pipelineId: string
  draftRevision: number | null
  check: PipelineCheckResult | null
  loadError: string | null
}
