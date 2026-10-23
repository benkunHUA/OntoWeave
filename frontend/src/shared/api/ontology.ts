import type {
  BindingDraftRequest,
  BindingProbeRequest,
  BindingRead,
  Conversation,
  DatasetColumn,
  DatasetKind,
  FilterCondition,
  GenerateMappingRequest,
  GenerateMappingResponse,
  IndexRun,
  MappingSuggestion,
  MappingValidation,
  ObjectPreview,
  ObjectRelationGroup,
  ObjectRelationPage,
  OntologyDatasetPage,
  OntologyObjectDetail,
  OntologyObjectPage,
  OntologyResources,
  OntologyResourceDefinition,
  OntologyResourceKind,
  PreviewBindingRequest,
  RelationInstancePage,
  RelationOverview,
  RelationPreview,
} from '../types'
import { request } from './client'

const ONTOLOGY_PATH = '/ontology'

type BindingResource = 'object-types' | 'relation-types'
type PageQuery = { page?: number; page_size?: number }

function operationHeaders(operationId?: string): Record<string, string> {
  return operationId ? { 'X-Operation-Id': operationId } : {}
}

function pathId(value: string): string {
  return encodeURIComponent(value)
}

function query(params: Record<string, string | number | undefined | null>): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') search.set(key, String(value))
  }
  return search.size ? `?${search.toString()}` : ''
}

function bindingPath(resource: BindingResource, id: string, suffix = ''): string {
  return `${ONTOLOGY_PATH}/${resource}/${pathId(id)}/binding${suffix}`
}

export function getOntologyResources(): Promise<OntologyResources> {
  return request<OntologyResources>(`${ONTOLOGY_PATH}/resources`)
}

export function getOntologyResourceDefinition(
  resourceKind: OntologyResourceKind,
  resourceId: string,
): Promise<OntologyResourceDefinition> {
  return request<OntologyResourceDefinition>(
    `${ONTOLOGY_PATH}/resources/${resourceKind}/${pathId(resourceId)}`,
  )
}

export function ensureOntologyConversation(
  resourceKind: 'object_type' | 'relation',
  resourceId: string,
): Promise<Conversation> {
  return request<Conversation>(
    `${ONTOLOGY_PATH}/conversations${query({ resource_kind: resourceKind, resource_id: resourceId })}`,
  )
}

export function listBindableDatasets(
  params: { keyword?: string; page?: number; size?: number } = {},
): Promise<OntologyDatasetPage> {
  return request<OntologyDatasetPage>(`${ONTOLOGY_PATH}/bindable-datasets${query(params)}`)
}

export function listBindableDatasetColumns(
  kind: DatasetKind,
  datasetId: string,
): Promise<DatasetColumn[]> {
  return request<DatasetColumn[]>(
    `${ONTOLOGY_PATH}/bindable-datasets/${kind}/${pathId(datasetId)}/columns`,
  )
}

export function getObjectBinding(objectTypeId: string): Promise<BindingRead> {
  return request<BindingRead>(bindingPath('object-types', objectTypeId))
}

export function putObjectBinding(
  objectTypeId: string,
  payload: BindingDraftRequest,
  operationId?: string,
): Promise<BindingRead> {
  return request<BindingRead>(bindingPath('object-types', objectTypeId), {
    method: 'PUT',
    headers: operationHeaders(operationId),
    body: JSON.stringify(payload),
  })
}

export function deleteObjectBinding(objectTypeId: string, operationId?: string): Promise<void> {
  return request<void>(bindingPath('object-types', objectTypeId), {
    method: 'DELETE',
    headers: operationHeaders(operationId),
  })
}

export function validateObjectBinding(
  objectTypeId: string,
  payload: BindingProbeRequest = {},
): Promise<MappingValidation> {
  return request<MappingValidation>(bindingPath('object-types', objectTypeId, '/validate'), {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function previewObjectBinding(
  objectTypeId: string,
  payload: PreviewBindingRequest = {},
): Promise<ObjectPreview> {
  return request<ObjectPreview>(bindingPath('object-types', objectTypeId, '/preview'), {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function suggestObjectBinding(
  objectTypeId: string,
  payload: BindingProbeRequest = {},
): Promise<MappingSuggestion> {
  return request<MappingSuggestion>(bindingPath('object-types', objectTypeId, '/suggest'), {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function generateObjectBinding(
  objectTypeId: string,
  payload: GenerateMappingRequest,
  operationId?: string,
): Promise<GenerateMappingResponse> {
  return request<GenerateMappingResponse>(bindingPath('object-types', objectTypeId, '/generate'), {
    method: 'POST',
    headers: operationHeaders(operationId),
    body: JSON.stringify(payload),
  })
}

export function listObjectBindingRuns(objectTypeId: string, limit = 20): Promise<IndexRun[]> {
  return request<IndexRun[]>(`${bindingPath('object-types', objectTypeId, '/runs')}${query({ limit })}`)
}

export function getRelationBinding(relationId: string): Promise<BindingRead> {
  return request<BindingRead>(bindingPath('relation-types', relationId))
}

export function putRelationBinding(
  relationId: string,
  payload: BindingDraftRequest,
  operationId?: string,
): Promise<BindingRead> {
  return request<BindingRead>(bindingPath('relation-types', relationId), {
    method: 'PUT',
    headers: operationHeaders(operationId),
    body: JSON.stringify(payload),
  })
}

export function deleteRelationBinding(relationId: string, operationId?: string): Promise<void> {
  return request<void>(bindingPath('relation-types', relationId), {
    method: 'DELETE',
    headers: operationHeaders(operationId),
  })
}

export async function validateRelationBinding(
  relationId: string,
  payload: PreviewBindingRequest = {},
): Promise<MappingValidation> {
  const preview = await previewRelationBinding(relationId, { ...payload, limit: payload.limit ?? 1 })
  return preview.validation
}

export function previewRelationBinding(
  relationId: string,
  payload: PreviewBindingRequest = {},
): Promise<RelationPreview> {
  return request<RelationPreview>(bindingPath('relation-types', relationId, '/preview'), {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function suggestRelationBinding(
  relationId: string,
  payload: BindingProbeRequest = {},
): Promise<MappingSuggestion> {
  return request<MappingSuggestion>(bindingPath('relation-types', relationId, '/suggest'), {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function generateRelationBinding(
  relationId: string,
  payload: GenerateMappingRequest,
  operationId?: string,
): Promise<GenerateMappingResponse> {
  return request<GenerateMappingResponse>(bindingPath('relation-types', relationId, '/generate'), {
    method: 'POST',
    headers: operationHeaders(operationId),
    body: JSON.stringify(payload),
  })
}

export function listRelationBindingRuns(relationId: string, limit = 20): Promise<IndexRun[]> {
  return request<IndexRun[]>(`${bindingPath('relation-types', relationId, '/runs')}${query({ limit })}`)
}

export function getRelationOverview(relationId: string): Promise<RelationOverview> {
  return request<RelationOverview>(`${ONTOLOGY_PATH}/relation-types/${pathId(relationId)}/overview`)
}

export function getIndexRun(runId: string): Promise<IndexRun> {
  return request<IndexRun>(`${ONTOLOGY_PATH}/runs/${pathId(runId)}`)
}

export function cancelIndexRun(runId: string, operationId?: string): Promise<IndexRun> {
  return request<IndexRun>(`${ONTOLOGY_PATH}/runs/${pathId(runId)}/cancel`, {
    method: 'POST',
    headers: operationHeaders(operationId),
  })
}

export function listObjects(
  objectTypeId: string,
  params: PageQuery & { q?: string; filters?: FilterCondition[] } = {},
): Promise<OntologyObjectPage> {
  const search = new URLSearchParams(query({ ...params, filters: undefined }).slice(1))
  if (params.filters?.length) search.set('filters', JSON.stringify(params.filters))
  const suffix = search.size ? `?${search.toString()}` : ''
  return request<OntologyObjectPage>(
    `${ONTOLOGY_PATH}/object-types/${pathId(objectTypeId)}/objects${suffix}`,
  )
}

export function getObjectDetail(
  objectTypeId: string,
  primaryKey: string,
): Promise<OntologyObjectDetail> {
  return request<OntologyObjectDetail>(
    `${ONTOLOGY_PATH}/object-types/${pathId(objectTypeId)}/object${query({ primary_key: primaryKey })}`,
  )
}

export function listObjectRelationGroups(
  objectTypeId: string,
  primaryKey: string,
  direction: 'both' | 'outgoing' | 'incoming' = 'both',
): Promise<ObjectRelationGroup[]> {
  return request<ObjectRelationGroup[]>(
    `${ONTOLOGY_PATH}/object-types/${pathId(objectTypeId)}/object-relations${query({
      primary_key: primaryKey,
      direction,
    })}`,
  )
}

export function listObjectRelations(
  objectTypeId: string,
  relationId: string,
  params: PageQuery & {
    primary_key: string
    direction?: 'outgoing' | 'incoming'
  },
): Promise<ObjectRelationPage> {
  return request<ObjectRelationPage>(
    `${ONTOLOGY_PATH}/object-types/${pathId(objectTypeId)}/object-relations/${pathId(relationId)}${query(params)}`,
  )
}

export function listRelationInstances(
  relationId: string,
  params: PageQuery & { from_primary_key?: string; to_primary_key?: string } = {},
): Promise<RelationInstancePage> {
  return request<RelationInstancePage>(
    `${ONTOLOGY_PATH}/relation-types/${pathId(relationId)}/instances${query(params)}`,
  )
}
