export type JsonObject = Record<string, unknown>

export interface Pipeline {
  id: string
  conversation_id: string
  name: string
  status: string
  draft_revision: number | null
  current_version_id: string | null
  current_version_number: number | null
  current_version_draft_revision: number | null
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
  row_limit: number
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

export interface PipelineAgentState {
  pipelineId: string
  draftRevision: number | null
  check: PipelineCheckResult | null
  loadError: string | null
}

export type * from './dataPipelineExecution'
