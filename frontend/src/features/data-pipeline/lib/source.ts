import type { PipelineSourceInput } from '../../../shared/types'

export interface SourceInputView {
  title: string
  datasetId: string
  versionLabel: string
  connection: string | null
  sourceTable: string | null
  showConnection: boolean
  note: string | null
}

export function sourceInputView(input: PipelineSourceInput): SourceInputView {
  const restricted = input.metadata_status === 'restricted'
  const missing = input.metadata_status === 'missing'
  const title = missing || !input.dataset_name ? input.dataset_id : input.dataset_name
  const version = input.dataset_version_number === null
    ? input.dataset_version_id ?? '版本未知'
    : `v${input.dataset_version_number}`
  return {
    title,
    datasetId: input.dataset_id,
    versionLabel: version,
    connection: restricted || missing ? null : input.connection_name,
    sourceTable: restricted || missing || !input.source_schema_name || !input.source_table_name
      ? null
      : `${input.source_schema_name}.${input.source_table_name}`,
    showConnection: !restricted && !missing,
    note: missing
      ? '元数据不可用'
      : restricted ? '元数据权限受限' : null,
  }
}
