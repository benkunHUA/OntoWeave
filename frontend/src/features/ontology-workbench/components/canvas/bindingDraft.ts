import { HttpError } from '../../../../shared/api/client'
import { getObjectBinding, getRelationBinding } from '../../../../shared/api/ontology'
import type {
  BindingRead,
  ObjectMapping,
  OntologyResourceDefinition,
  RelationMapping,
} from '../../../../shared/types'
import {
  emptyObjectMapping,
  emptyRelationMapping,
  sanitizeObjectMapping,
  sanitizeRelationMapping,
  type MappingMode,
} from '../../lib/mapping'
import type { DraftValue } from './draftState'

export async function fetchBinding(
  mode: MappingMode,
  resourceId: string,
): Promise<BindingRead | null> {
  try {
    return mode === 'object'
      ? await getObjectBinding(resourceId)
      : await getRelationBinding(resourceId)
  } catch (reason) {
    if (reason instanceof HttpError && reason.status === 404) return null
    throw reason
  }
}

export function draftFromBinding(
  binding: BindingRead | null,
  definition: OntologyResourceDefinition,
  mode: MappingMode,
): DraftValue {
  const raw = binding?.draft_mapping ?? binding?.mapping ?? null
  const mapping = mode === 'object'
    ? sanitizeObjectMapping(
      raw && 'primary_key_attribute_id' in raw ? raw as ObjectMapping : emptyObjectMapping(definition),
      definition,
    )
    : sanitizeRelationMapping(
      raw && 'from_key_column' in raw ? raw as RelationMapping : emptyRelationMapping(definition),
    )
  return {
    datasetKind: binding?.draft_dataset_kind ?? null,
    datasetId: binding?.draft_dataset_id ?? null,
    mapping,
    revision: binding?.revision ?? 0,
  }
}
