export type ConnectionKind = 'mysql'
export type ConnectionStatus = 'draft' | 'active'
export type ConnectionTestStatus = 'untested' | 'success' | 'failed'
export type SyncMode = 'cdc' | 'snapshot'
export type SyncRunStatus =
  | 'queued'
  | 'preparing'
  | 'submitted'
  | 'running'
  | 'stopping'
  | 'succeeded'
  | 'stopped'
  | 'failed'

export interface MysqlConfig {
  host?: string
  port?: number
  database?: string
  username?: string
  useSsl?: boolean
}

export interface DataConnection {
  id: string
  conversation_id: string
  name: string
  kind: ConnectionKind
  config: MysqlConfig
  config_version: number
  status: ConnectionStatus
  last_test_status: ConnectionTestStatus
  last_test_at: string | null
  last_test_error: string | null
  last_test_details: Record<string, unknown> | null
  secret_configured: boolean
  last_sync_status: SyncRunStatus | null
  last_sync_at: string | null
  created_at: string
  updated_at: string
}

export interface DataConnectionPage {
  items: DataConnection[]
  total: number
  limit: number
  offset: number
}

export interface ConnectionTestResult {
  status: 'success' | 'failed'
  latency_ms: number
  server_version: string | null
  error_code: string | null
  message: string | null
  tested_at: string | null
  details: Record<string, unknown> | null
}

export interface TableSummary {
  schema: string
  name: string
  type: string
  comment: string | null
  estimated_rows: number | null
  has_primary_key: boolean
}

export interface TableList {
  tables: TableSummary[]
  warnings: string[]
}

export interface Column {
  name: string
  ordinal_position: number
  data_type: string
  column_type: string | null
  nullable: boolean
  default: string | null
  is_primary_key: boolean
  comment: string | null
}

export interface TableDetail {
  schema: string
  table: string
  type: string
  comment: string | null
  columns: Column[]
  primary_key_columns: string[]
}

export interface SyncTask {
  id: string
  connection_id: string
  dataset_id: string
  schema: string
  table: string
  sync_mode: SyncMode
  version_interval_seconds: number
  primary_key_columns: string[]
  status: string
  created_at: string
  updated_at: string
}

export interface SyncTaskSelection {
  schema: string
  table: string
  sync_mode: SyncMode
  version_interval_seconds?: number | null
}

export interface SyncRun {
  id: string
  sync_task_id: string
  dataset_id: string
  operation_id: string | null
  run_mode: SyncMode
  status: SyncRunStatus
  flink_job_id: string | null
  flink_job_state: string | null
  snapshot_completed_at: string | null
  last_checkpoint_at: string | null
  next_version_due_at: string | null
  savepoint_path: string | null
  error_code: string | null
  error_message: string | null
  started_at: string | null
  finished_at: string | null
  created_at: string
}

export interface DatasetVersion {
  id: string
  version_number: number
  row_count: number
  row_count_estimated: boolean
  column_count: number
  byte_size: number
  schema: unknown
  committed_at: string | null
  published_at: string | null
  version_source: string
  source_run_id: string | null
  storage_path: string | null
}

export interface Dataset {
  id: string
  connection_id: string
  sync_task_id: string
  name: string
  current_version_id: string | null
  current_version: DatasetVersion | null
  versions: DatasetVersion[]
  version_count: number
  source_schema: string | null
  source_table: string | null
  created_at: string
  updated_at: string
}

export interface DatasetPreview {
  dataset_id: string
  version_id: string | null
  version_number: number | null
  snapshot_id: number
  live: boolean
  columns: PreviewColumn[]
  row_count: number
  rows: Array<Record<string, unknown>>
  truncated: boolean
}

export interface PreviewColumn {
  name: string
  type: string
  nullable: boolean
}

export interface DatasetVersionsPage {
  dataset_id: string
  total: number
  limit: number
  offset: number
  versions: DatasetVersion[]
}

export interface DataConnectionState {
  connection: DataConnection
  tasks: SyncTask[]
  recent_runs: SyncRun[]
  datasets: Dataset[]
  missing_steps: string[]
  last_event_id: string | null
}

export type MissingStep =
  | 'SAVE_CONNECTION'
  | 'TEST_CONNECTION'
  | 'SELECT_TABLES'
  | 'CREATE_SYNC'
  | 'RUN_SYNC'

export interface DataConnectionCreate {
  name: string
  kind: ConnectionKind
  config?: MysqlConfig
  secret?: { password: string }
}

export interface DataConnectionUpdate {
  name?: string
  config?: MysqlConfig
  secret?: { password: string }
}
