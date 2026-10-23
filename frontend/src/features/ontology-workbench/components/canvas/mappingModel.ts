import type {
  DatasetColumn,
  MappingIssue,
  ObjectMapping,
  OntologyProperty,
  OntologyResourceDefinition,
  RelationMapping,
} from '../../../../shared/types'
import { PROPERTY_DATATYPE_LABELS, primaryKeyProperty } from '../../lib/mapping'
import { CANVAS_COPY } from './copy'
import type { MappingField } from './types'

function propertyBadges(property: OntologyProperty): string[] {
  const badges: string[] = []
  if (property.is_primary_key) badges.push(CANVAS_COPY.primaryBadge)
  if (property.is_title_key) badges.push(CANVAS_COPY.titleBadge)
  if (property.required) badges.push(CANVAS_COPY.requiredBadge)
  if (property.is_array) badges.push(CANVAS_COPY.arrayBadge)
  return badges
}

function issueSets(blocking: MappingIssue[], warnings: MappingIssue[]) {
  return {
    blockingAttributes: new Set(blocking.map((item) => item.attribute_id).filter(Boolean)),
    blockingColumns: new Set(blocking.map((item) => item.column).filter(Boolean)),
    warningAttributes: new Set(warnings.map((item) => item.attribute_id).filter(Boolean)),
    warningColumns: new Set(warnings.map((item) => item.column).filter(Boolean)),
  }
}

export function buildLeftFields(
  mode: 'object' | 'relation',
  definition: OntologyResourceDefinition | null,
  mapping: ObjectMapping | RelationMapping,
  blocking: MappingIssue[],
  warnings: MappingIssue[],
): MappingField[] {
  if (!definition) return []
  const issues = issueSets(blocking, warnings)
  if (mode === 'object') {
    const objectMapping = mapping as ObjectMapping
    const mapped = new Set(objectMapping.attributes?.map((item) => item.attribute_id) ?? [])
    return definition.properties.map((property) => ({
      id: `attribute:${property.id}`,
      label: property.name,
      typeLabel: PROPERTY_DATATYPE_LABELS[property.datatype],
      meta: property.required ? CANVAS_COPY.requiredBadge : null,
      badges: propertyBadges(property),
      mapped: mapped.has(property.id),
      blocking: issues.blockingAttributes.has(property.id),
      warning: issues.warningAttributes.has(property.id),
      disabled: property.is_array,
    }))
  }
  const relationMapping = mapping as RelationMapping
  const sourceKey = primaryKeyProperty(definition.source)
  const targetKey = primaryKeyProperty(definition.target)
  return [
    {
      id: 'endpoint:from', label: CANVAS_COPY.sourcePrimary,
      typeLabel: sourceKey ? PROPERTY_DATATYPE_LABELS[sourceKey.datatype] : CANVAS_COPY.missingPrimary,
      meta: definition.source?.name ?? definition.source_type_name,
      badges: [CANVAS_COPY.fromKey], mapped: Boolean(relationMapping.from_key_column),
      blocking: !sourceKey || blocking.some((item) => item.code.startsWith('FROM_') || item.code === 'SOURCE_PRIMARY_KEY_MISSING'),
      warning: false, disabled: !sourceKey,
    },
    {
      id: 'endpoint:to', label: CANVAS_COPY.targetPrimary,
      typeLabel: targetKey ? PROPERTY_DATATYPE_LABELS[targetKey.datatype] : CANVAS_COPY.missingPrimary,
      meta: definition.target?.name ?? definition.target_type_name,
      badges: [CANVAS_COPY.toKey], mapped: Boolean(relationMapping.to_key_column),
      blocking: !targetKey || blocking.some((item) => item.code.startsWith('TO_') || item.code === 'TARGET_PRIMARY_KEY_MISSING'),
      warning: false, disabled: !targetKey,
    },
  ]
}

export function buildRightFields(
  columns: DatasetColumn[],
  mapping: ObjectMapping | RelationMapping,
  blocking: MappingIssue[],
  warnings: MappingIssue[],
): MappingField[] {
  const objectMapping = mapping as ObjectMapping
  const relationMapping = mapping as RelationMapping
  const mapped = new Set([
    ...(objectMapping.attributes?.map((item) => item.column) ?? []),
    relationMapping.from_key_column,
    relationMapping.to_key_column,
  ].filter(Boolean))
  const issues = issueSets(blocking, warnings)
  return columns.map((column) => ({
    id: `column:${column.name}`,
    label: column.name,
    typeLabel: column.type,
    meta: column.optional ? CANVAS_COPY.columnOptional : CANVAS_COPY.columnRequired,
    badges: [],
    mapped: mapped.has(column.name),
    blocking: issues.blockingColumns.has(column.name),
    warning: issues.warningColumns.has(column.name),
  }))
}

export function buildMappingEdges(
  mode: 'object' | 'relation',
  mapping: ObjectMapping | RelationMapping,
  definition: OntologyResourceDefinition | null,
  columns: DatasetColumn[],
  onRemove: (edgeId: string) => void,
) {
  if (mode === 'object') {
    const objectMapping = mapping as ObjectMapping
    return (objectMapping.attributes ?? []).flatMap((item) => {
      const property = definition?.properties.find((entry) => entry.id === item.attribute_id)
      const column = columns.find((entry) => entry.name === item.column)
      if (!property || !column) return []
      return [{
        id: `object:${item.attribute_id}`,
        source: 'ontology-attributes',
        sourceHandle: `attribute:${item.attribute_id}`,
        target: 'dataset-columns',
        targetHandle: `column:${item.column}`,
        type: 'mapping',
        data: { label: `${property.name} → ${item.column}`, onRemove },
      }]
    })
  }
  const relationMapping = mapping as RelationMapping
  return ([['from', relationMapping.from_key_column], ['to', relationMapping.to_key_column]] as const)
    .flatMap(([endpoint, columnName]) => {
      const column = columns.find((entry) => entry.name === columnName)
      if (!columnName || !column) return []
      return [{
        id: `relation:${endpoint}`,
        source: 'ontology-attributes',
        sourceHandle: `endpoint:${endpoint}`,
        target: 'dataset-columns',
        targetHandle: `column:${columnName}`,
        type: 'mapping',
        data: { label: `${endpoint === 'from' ? CANVAS_COPY.fromKey : CANVAS_COPY.toKey} → ${columnName}`, onRemove },
      }]
    })
}
