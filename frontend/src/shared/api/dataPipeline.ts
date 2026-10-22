import type {
  DatasetPreview,
  DatasetSchema,
  InputDatasetPage,
  NodeCatalog,
  Pipeline,
  PipelineBuild,
  PipelineBuildRequest,
  PipelineCheckResult,
  PipelineCopy,
  PipelineCreate,
  PipelineDraftCommand,
  PipelineDraftResponse,
  PipelineNodePositionUpdate,
  PipelinePage,
  PipelinePreview,
  PipelinePreviewRequest,
  PipelineSchedule,
  PipelineSchedulePutRequest,
  PipelineScheduleRun,
  PipelineSource,
  PipelineUpdate,
} from '../types'
import { request } from './client'

function operationHeaders(operationId: string): Record<string, string> {
  return { 'X-Operation-Id': operationId }
}

export function listPipelines(limit = 100, offset = 0): Promise<PipelinePage> {
  return request<PipelinePage>(`/pipelines?limit=${limit}&offset=${offset}`)
}

export function getPipeline(pipelineId: string): Promise<Pipeline> {
  return request<Pipeline>(`/pipelines/${pipelineId}`)
}

export function createPipeline(payload: PipelineCreate, operationId: string): Promise<Pipeline> {
  return request<Pipeline>('/pipelines', {
    method: 'POST',
    headers: operationHeaders(operationId),
    body: JSON.stringify(payload),
  })
}

export function renamePipeline(
  pipelineId: string,
  payload: PipelineUpdate,
  operationId: string,
): Promise<Pipeline> {
  return request<Pipeline>(`/pipelines/${pipelineId}`, {
    method: 'PATCH',
    headers: operationHeaders(operationId),
    body: JSON.stringify(payload),
  })
}

export function archivePipeline(pipelineId: string, operationId: string): Promise<void> {
  return request<void>(`/pipelines/${pipelineId}`, {
    method: 'DELETE',
    headers: operationHeaders(operationId),
  })
}

export function copyPipeline(
  pipelineId: string,
  payload: PipelineCopy,
  operationId: string,
): Promise<Pipeline> {
  return request<Pipeline>(`/pipelines/${pipelineId}/copy`, {
    method: 'POST',
    headers: operationHeaders(operationId),
    body: JSON.stringify(payload),
  })
}

export function getPipelineDraft(pipelineId: string): Promise<PipelineDraftResponse> {
  return request<PipelineDraftResponse>(`/pipelines/${pipelineId}/draft`)
}

export function applyPipelineDraftCommand(
  pipelineId: string,
  payload: PipelineDraftCommand,
): Promise<PipelineDraftResponse> {
  return request<PipelineDraftResponse>(`/pipelines/${pipelineId}/draft/commands`, {
    method: 'POST',
    headers: operationHeaders(payload.operation_id),
    body: JSON.stringify(payload),
  })
}

export function movePipelineNodes(
  pipelineId: string,
  positions: PipelineNodePositionUpdate[],
  operationId: string,
): Promise<PipelineDraftResponse> {
  return request<PipelineDraftResponse>(`/pipelines/${pipelineId}/draft/positions`, {
    method: 'POST',
    headers: operationHeaders(operationId),
    body: JSON.stringify({ positions }),
  })
}

export function checkPipeline(pipelineId: string, revision: number): Promise<PipelineCheckResult> {
  return request<PipelineCheckResult>(`/pipelines/${pipelineId}/check?revision=${revision}`)
}

export function undoPipelineTurn(
  pipelineId: string,
  turnId: string,
  revision: number,
  operationId: string,
): Promise<PipelineDraftResponse> {
  return request<PipelineDraftResponse>(`/pipelines/${pipelineId}/turns/${turnId}/undo`, {
    method: 'POST',
    headers: operationHeaders(operationId),
    body: JSON.stringify({ revision }),
  })
}

export function getNodeCatalog(): Promise<NodeCatalog> {
  return request<NodeCatalog>('/pipelines/node-catalog')
}

export function listPipelineInputDatasets(
  limit = 100,
  offset = 0,
): Promise<InputDatasetPage> {
  return request<InputDatasetPage>(`/pipelines/input-datasets?limit=${limit}&offset=${offset}`)
}

export function getPipelineDatasetSchema(datasetId: string): Promise<DatasetSchema> {
  return request<DatasetSchema>(`/pipelines/input-datasets/${datasetId}/schema`)
}

export function previewPipelineNode(
  pipelineId: string,
  payload: PipelinePreviewRequest,
  operationId: string,
): Promise<PipelinePreview> {
  return request<PipelinePreview>(`/pipelines/${pipelineId}/previews`, {
    method: 'POST',
    headers: operationHeaders(operationId),
    body: JSON.stringify(payload),
  })
}

export function cancelPipelinePreview(pipelineId: string): Promise<void> {
  return request<void>(`/pipelines/${pipelineId}/previews/cancel`, { method: 'POST' })
}

export function createPipelineBuild(
  pipelineId: string,
  payload: PipelineBuildRequest,
): Promise<PipelineBuild> {
  return request<PipelineBuild>(`/pipelines/${pipelineId}/builds`, {
    method: 'POST',
    headers: operationHeaders(payload.operation_id),
    body: JSON.stringify(payload),
  })
}

export function getPipelineBuild(pipelineId: string, buildId: string): Promise<PipelineBuild> {
  return request<PipelineBuild>(`/pipelines/${pipelineId}/builds/${buildId}`)
}

export function getLatestPipelineBuild(pipelineId: string): Promise<PipelineBuild> {
  return request<PipelineBuild>(`/pipelines/${pipelineId}/builds/latest`)
}

export function cancelPipelineBuild(
  pipelineId: string,
  buildId: string,
  operationId: string,
): Promise<{ status: string }> {
  return request<{ status: string }>(`/pipelines/${pipelineId}/builds/${buildId}/cancel`, {
    method: 'POST',
    headers: operationHeaders(operationId),
  })
}

export function getPipelineOutputSources(
  pipelineId: string,
  datasetId: string,
  versionId?: string | null,
): Promise<PipelineSource> {
  const query = versionId ? `?version_id=${encodeURIComponent(versionId)}` : ''
  return request<PipelineSource>(
    `/pipelines/${pipelineId}/datasets/${datasetId}/sources${query}`,
  )
}

export function getPipelineDatasetPreview(
  pipelineId: string,
  datasetId: string,
  versionId?: string | null,
  limit = 100,
): Promise<DatasetPreview> {
  const params = new URLSearchParams({ limit: String(limit) })
  if (versionId) params.set('version_id', versionId)
  return request<DatasetPreview>(
    `/pipelines/${pipelineId}/datasets/${datasetId}/preview?${params.toString()}`,
  )
}

export function getPipelineSchedule(pipelineId: string): Promise<PipelineSchedule> {
  return request<PipelineSchedule>(`/pipelines/${pipelineId}/schedule`)
}

export function putPipelineSchedule(
  pipelineId: string,
  payload: PipelineSchedulePutRequest,
  operationId: string,
): Promise<PipelineSchedule> {
  return request<PipelineSchedule>(`/pipelines/${pipelineId}/schedule`, {
    method: 'PUT',
    headers: operationHeaders(operationId),
    body: JSON.stringify(payload),
  })
}

export function pausePipelineSchedule(
  pipelineId: string,
  operationId: string,
): Promise<PipelineSchedule> {
  return request<PipelineSchedule>(`/pipelines/${pipelineId}/schedule/pause`, {
    method: 'POST',
    headers: operationHeaders(operationId),
  })
}

export function resumePipelineSchedule(
  pipelineId: string,
  operationId: string,
): Promise<PipelineSchedule> {
  return request<PipelineSchedule>(`/pipelines/${pipelineId}/schedule/resume`, {
    method: 'POST',
    headers: operationHeaders(operationId),
  })
}

export function listPipelineScheduleRuns(
  pipelineId: string,
  limit = 50,
  offset = 0,
): Promise<PipelineScheduleRun[]> {
  return request<PipelineScheduleRun[]>(
    `/pipelines/${pipelineId}/schedule/runs?limit=${limit}&offset=${offset}`,
  )
}
