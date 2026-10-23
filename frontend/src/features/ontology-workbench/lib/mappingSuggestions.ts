import type {
  DatasetColumn,
  MappingSuggestion,
  ObjectMapping,
  OntologyResourceDefinition,
  RelationMapping,
} from '../../../shared/types'
import {
  connectObjectAttribute,
  connectRelationEndpoint,
} from './mappingConnections'
import {
  columnMap,
  primaryKeyProperty,
  propertyMap,
} from './mappingTypes'

export type SuggestionMerge = {
  mapping: ObjectMapping | RelationMapping
  added: number
}

export function mergeObjectSuggestion(
  mapping: ObjectMapping,
  suggestion: MappingSuggestion,
  definition: OntologyResourceDefinition,
  columns: DatasetColumn[],
): SuggestionMerge {
  const suggested = suggestion.mapping as ObjectMapping
  const properties = propertyMap(definition)
  const columnsByName = columnMap(columns)
  let next = mapping
  let added = 0
  for (const item of suggested.attributes ?? []) {
    const property = properties.get(item.attribute_id)
    const column = columnsByName.get(item.column)
    if (!property || !column) continue
    const result = connectObjectAttribute(next, property, column)
    if (!result.ok) continue
    next = result.mapping as ObjectMapping
    added += 1
  }
  return { mapping: next, added }
}

export function mergeRelationSuggestion(
  mapping: RelationMapping,
  suggestion: MappingSuggestion,
  definition: OntologyResourceDefinition,
  columns: DatasetColumn[],
): SuggestionMerge {
  const suggested = suggestion.mapping as RelationMapping
  const columnsByName = columnMap(columns)
  const fromType = primaryKeyProperty(definition.source)?.datatype
  const toType = primaryKeyProperty(definition.target)?.datatype
  let next = mapping
  let added = 0
  if (!next.from_key_column && fromType && suggested.from_key_column) {
    const column = columnsByName.get(suggested.from_key_column)
    const result = column ? connectRelationEndpoint(next, 'from', fromType, column) : null
    if (result?.ok) {
      next = result.mapping as RelationMapping
      added += 1
    }
  }
  if (!next.to_key_column && toType && suggested.to_key_column) {
    const column = columnsByName.get(suggested.to_key_column)
    const result = column ? connectRelationEndpoint(next, 'to', toType, column) : null
    if (result?.ok) {
      next = result.mapping as RelationMapping
      added += 1
    }
  }
  return { mapping: next, added }
}
