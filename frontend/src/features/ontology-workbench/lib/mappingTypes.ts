import type {
  AttributeMapping,
  DatasetColumn,
  ObjectMapping,
  OntologyProperty,
  OntologyResourceDefinition,
  OntologyTypeDefinition,
  PropertyDatatype,
  RelationMapping,
} from '../../../shared/types'

export type MappingMode = 'object' | 'relation'
export type RelationEndpoint = 'from' | 'to'

const TYPE_ALIASES: Record<string, string> = {
  bool: 'boolean',
  bigint: 'long',
  integer: 'int',
  varchar: 'string',
  'character varying': 'string',
  'timestamp without time zone': 'timestamp',
  'timestamp with time zone': 'timestamptz',
  'timestamptz(6)': 'timestamptz',
}

const SUPPORTED_TYPES: Record<PropertyDatatype, readonly string[]> = {
  text: ['string', 'uuid'],
  integer: ['int', 'long'],
  decimal: ['decimal', 'int', 'long'],
  boolean: ['boolean'],
  date: ['date'],
  timestamp: ['timestamp', 'timestamptz'],
  enum: ['string'],
}

const PRIMARY_KEY_TYPES: readonly PropertyDatatype[] = [
  'text', 'integer', 'decimal', 'date', 'timestamp',
]

export const PROPERTY_DATATYPE_LABELS: Record<PropertyDatatype, string> = {
  text: '文本',
  integer: '整数',
  decimal: '小数',
  boolean: '布尔',
  date: '日期',
  timestamp: '时间戳',
  enum: '枚举',
}

export function primaryKeyProperty(type: OntologyTypeDefinition | null | undefined) {
  return type?.properties.find((property) => property.is_primary_key) ?? null
}

export function titleKeyProperty(type: OntologyTypeDefinition | null | undefined) {
  return type?.properties.find((property) => property.is_title_key) ?? null
}

export function supportsPrimaryKey(property: OntologyProperty): boolean {
  return !property.is_array && PRIMARY_KEY_TYPES.includes(property.datatype)
}

export function requiredAttributeIds(definition: OntologyResourceDefinition): string[] {
  return definition.properties
    .filter((property) => property.required)
    .map((property) => property.id)
}

export function emptyObjectMapping(definition: OntologyResourceDefinition): ObjectMapping {
  return {
    primary_key_attribute_id: primaryKeyProperty(definition)?.id ?? '',
    title_key_attribute_id: titleKeyProperty(definition)?.id ?? null,
    attributes: [],
    required_attribute_ids: requiredAttributeIds(definition),
  }
}

export function emptyRelationMapping(definition: OntologyResourceDefinition): RelationMapping {
  return {
    from_key_column: '',
    from_key_datatype: primaryKeyProperty(definition.source)?.datatype ?? 'text',
    to_key_column: '',
    to_key_datatype: primaryKeyProperty(definition.target)?.datatype ?? 'text',
  }
}

export function splitDatasetType(value: string): string {
  const normalized = value.trim().toLowerCase().replace(/\s+/g, ' ')
  const withoutParameters = normalized.replace(/\s*\([^)]*\)\s*$/, '')
  return TYPE_ALIASES[normalized] ?? TYPE_ALIASES[withoutParameters] ?? withoutParameters
}

export function isDatasetTypeCompatible(
  datatype: PropertyDatatype,
  columnType: string,
): boolean {
  return SUPPORTED_TYPES[datatype].includes(splitDatasetType(columnType))
}

export function incompatibleTypeMessage(
  property: OntologyProperty,
  column: DatasetColumn,
): string {
  return `${PROPERTY_DATATYPE_LABELS[property.datatype]}属性不能映射到 ${column.type} 字段`
}

export function columnMap(columns: DatasetColumn[]): Map<string, DatasetColumn> {
  return new Map(columns.map((column) => [column.name, column]))
}

export function propertyMap(
  definition: OntologyResourceDefinition,
): Map<string, OntologyProperty> {
  return new Map(definition.properties.map((property) => [property.id, property]))
}

export function mappingAttribute(property: OntologyProperty, column: string): AttributeMapping {
  return {
    attribute_id: property.id,
    column,
    datatype: property.datatype,
    required: property.required,
    is_array: property.is_array,
    enum_values: property.enum_values,
  }
}

export function sanitizeObjectMapping(
  mapping: ObjectMapping,
  definition: OntologyResourceDefinition,
): ObjectMapping {
  const properties = propertyMap(definition)
  const seenAttributes = new Set<string>()
  const seenColumns = new Set<string>()
  const attributes = mapping.attributes.filter((attribute) => {
    if (!properties.has(attribute.attribute_id) || seenAttributes.has(attribute.attribute_id)) {
      return false
    }
    if (!attribute.column || seenColumns.has(attribute.column)) return false
    seenAttributes.add(attribute.attribute_id)
    seenColumns.add(attribute.column)
    return true
  })
  return {
    primary_key_attribute_id: properties.has(mapping.primary_key_attribute_id)
      ? mapping.primary_key_attribute_id
      : primaryKeyProperty(definition)?.id ?? '',
    title_key_attribute_id: mapping.title_key_attribute_id &&
      properties.has(mapping.title_key_attribute_id)
      ? mapping.title_key_attribute_id
      : null,
    attributes,
    required_attribute_ids: requiredAttributeIds(definition),
  }
}

export function sanitizeRelationMapping(mapping: RelationMapping): RelationMapping {
  return {
    from_key_column: mapping.from_key_column ?? '',
    from_key_datatype: mapping.from_key_datatype ?? 'text',
    to_key_column: mapping.to_key_column ?? '',
    to_key_datatype: mapping.to_key_datatype ?? 'text',
  }
}
