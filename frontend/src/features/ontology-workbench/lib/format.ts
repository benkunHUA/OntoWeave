import type { BindingRead, ConversationEvent, OntologyResource } from '../../../shared/types'
import {
  HIDDEN_ONTOLOGY_EVENT_PREFIXES,
  RESOURCE_REF_SEPARATOR,
  RESOURCE_TABS,
  type ResourceCategory,
  type ResourceTabDefinition,
} from './constants'

export interface ResourceRef {
  kind: 'object_type' | 'relation'
  id: string
}

export function parseResourceRef(raw: string | null | undefined): ResourceRef | null {
  if (!raw) return null
  const [kind, id, ...rest] = raw.split(RESOURCE_REF_SEPARATOR)
  if (rest.length || !id || (kind !== 'object_type' && kind !== 'relation')) return null
  return { kind, id }
}

export function formatResourceRef(resource: Pick<OntologyResource, 'id' | 'kind'>): string {
  return `${resource.kind}${RESOURCE_REF_SEPARATOR}${resource.id}`
}

export function resourceKey(resource: Pick<OntologyResource, 'id' | 'kind'> | null): string {
  return resource ? formatResourceRef(resource) : ''
}

export function resourceCategory(resource: OntologyResource): ResourceCategory {
  if (resource.kind === 'object_type') return 'object_type'
  const relationKind = resource.relation_kind ?? (resource.cardinality?.toLowerCase().includes('many') ? 'MANY_TO_MANY' : 'FK')
  return relationKind === 'MANY_TO_MANY' ? 'many_to_many' : 'foreign_key'
}

export function resourceTabs(resource: OntologyResource): readonly ResourceTabDefinition[] {
  return RESOURCE_TABS[resourceCategory(resource)]
}

export function resourceDirection(resource: OntologyResource): string {
  if (resource.kind === 'object_type') return '对象类型'
  if (resource.direction_label) return resource.direction_label
  return [resource.source_type_name, resource.name, resource.target_type_name].filter(Boolean).join(' → ')
}

export function bindingLabel(binding: BindingRead | null): string {
  if (!binding?.binding_id && !binding?.id && !binding?.draft_dataset_id) return '未绑定'
  if (binding.active_dataset_id || binding.current_index_version_id) return '已生成'
  return '已绑定'
}

export function isVisibleOntologyEvent(event: ConversationEvent): boolean {
  return !HIDDEN_ONTOLOGY_EVENT_PREFIXES.some((prefix) => event.event_type.startsWith(prefix))
}
