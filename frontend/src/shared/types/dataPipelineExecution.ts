import type { JsonObject } from './dataPipeline'

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
  dataset_id: string | null
  dataset_version_id: string | null
  output_type: string
  status: string
  stage: string | null
  engine_job_id: string | null
  log_uri: string | null
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
  log_uri: string | null
  started_at: string | null
  finished_at: string | null
  outputs: PipelineBuildOutput[]
}

export interface PipelineSourceOutput {
  output_id: string
  name: string
  version_number: number
  row_count: number
  published_at: string | null
}

export interface PipelineSourceBuild {
  build_id: string
  status: string
  trigger: string
  started_at: string | null
  finished_at: string | null
}

export interface PipelineSourcePipelineVersion {
  version_id: string
  version_number: number
  created_at: string | null
}

export interface PipelineSourceInput {
  dataset_kind: string
  dataset_id: string
  dataset_version_id: string | null
  dataset_version_number: number | null
  schema_hash: string | null
  dataset_name: string | null
  connection_name: string | null
  source_schema_name: string | null
  source_table_name: string | null
  metadata_status: string | null
}

export interface PipelineSource {
  pipeline_id: string
  dataset_id: string
  version_id: string | null
  output: PipelineSourceOutput
  build: PipelineSourceBuild | null
  pipeline_version: PipelineSourcePipelineVersion | null
  inputs: PipelineSourceInput[]
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
  started_at: string | null
  finished_at: string | null
}
