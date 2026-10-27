import type {
  ConnectionTestResult,
  DataConnection,
  DataConnectionCreate,
  DataConnectionPage,
  DataConnectionState,
  DataConnectionUpdate,
  Dataset,
  DatasetPreview,
  DatasetVersion,
  DatasetVersionsPage,
  SyncRun,
  SyncTask,
  SyncTaskSelection,
  TableDetail,
  TableList,
  Conversation,
} from '../types'
import { request } from './client'

function operationHeaders(operationId?: string): Record<string, string> {
  return operationId ? { 'X-Operation-Id': operationId } : {}
}

export function listDataConnections(): Promise<DataConnectionPage> {
  return request<DataConnectionPage>('/data-connections?limit=100')
}

export function getDataConnection(connectionId: string): Promise<DataConnection> {
  return request<DataConnection>(`/data-connections/${connectionId}`)
}

export function createDataConnection(payload: DataConnectionCreate): Promise<DataConnection> {
  return request<DataConnection>('/data-connections', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateDataConnection(
  connectionId: string,
  payload: DataConnectionUpdate,
  operationId?: string,
): Promise<DataConnection> {
  return request<DataConnection>(`/data-connections/${connectionId}`, {
    method: 'PATCH',
    headers: operationHeaders(operationId),
    body: JSON.stringify(payload),
  })
}

export function testDataConnection(
  connectionId: string,
  operationId?: string,
): Promise<ConnectionTestResult> {
  return request<ConnectionTestResult>(`/data-connections/${connectionId}/test`, {
    method: 'POST',
    headers: operationHeaders(operationId),
  })
}

export function listTables(connectionId: string): Promise<TableList> {
  return request<TableList>(`/data-connections/${connectionId}/tables`)
}

export function describeTable(
  connectionId: string,
  schema: string,
  table: string,
): Promise<TableDetail> {
  const schemaPart = encodeURIComponent(schema)
  const tablePart = encodeURIComponent(table)
  return request<TableDetail>(`/data-connections/${connectionId}/tables/${schemaPart}/${tablePart}`)
}

export function getDataConnectionState(connectionId: string): Promise<DataConnectionState> {
  return request<DataConnectionState>(`/data-connections/${connectionId}/state`)
}

export function ensureDataConnectionConversation(connectionId: string): Promise<Conversation> {
  return request<Conversation>(`/data-connections/${connectionId}/conversation`, {
    method: 'POST',
  })
}

export function saveSyncTasks(
  connectionId: string,
  tables: SyncTaskSelection[],
  operationId?: string,
): Promise<{ tasks: SyncTask[] }> {
  return request<{ tasks: SyncTask[] }>(`/data-connections/${connectionId}/sync-tasks`, {
    method: 'POST',
    headers: operationHeaders(operationId),
    body: JSON.stringify({ tables }),
  })
}

export function listSyncTasks(connectionId: string): Promise<{ tasks: SyncTask[] }> {
  return request<{ tasks: SyncTask[] }>(`/data-connections/${connectionId}/sync-tasks`)
}

export function triggerSync(
  connectionId: string,
  syncTaskIds: string[],
  operationId: string,
): Promise<{ operation_id: string | null; runs: SyncRun[] }> {
  return request<{ operation_id: string | null; runs: SyncRun[] }>(
    `/data-connections/${connectionId}/sync-runs`,
    {
      method: 'POST',
      body: JSON.stringify({ sync_task_ids: syncTaskIds, operation_id: operationId }),
    },
  )
}

export function listSyncRuns(connectionId: string): Promise<{ runs: SyncRun[] }> {
  return request<{ runs: SyncRun[] }>(`/data-connections/${connectionId}/sync-runs`)
}

export function stopSyncRun(runId: string, operationId?: string): Promise<SyncRun> {
  return request<SyncRun>(`/sync-runs/${runId}/stop`, {
    method: 'POST',
    headers: operationHeaders(operationId),
  })
}

export function listDatasets(connectionId: string): Promise<Dataset[]> {
  return request<Dataset[]>(`/data-connections/${connectionId}/datasets`)
}

export function getDataset(datasetId: string): Promise<Dataset> {
  return request<Dataset>(`/datasets/${datasetId}`)
}

export function listDatasetVersions(
  datasetId: string,
  limit = 20,
  offset = 0,
): Promise<DatasetVersionsPage> {
  return request<DatasetVersionsPage>(
    `/datasets/${datasetId}/versions?limit=${limit}&offset=${offset}`,
  )
}

export function getDatasetVersion(datasetId: string, versionId: string): Promise<DatasetVersion> {
  return request<DatasetVersion>(`/datasets/${datasetId}/versions/${versionId}`)
}

export function getDatasetPreview(
  datasetId: string,
  versionId?: string | null,
  limit = 100,
  live = false,
): Promise<DatasetPreview> {
  const params = new URLSearchParams({
    limit: String(limit),
    live: live ? 'true' : 'false',
  })
  if (versionId) params.set('version_id', versionId)
  return request<DatasetPreview>(`/datasets/${datasetId}/preview?${params.toString()}`)
}
