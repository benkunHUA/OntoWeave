import type {
  DatasetColumn,
  MappingIssue,
  ObjectMapping,
  OntologyResourceDefinition,
  RelationMapping,
} from '../../../shared/types'
import {
  columnMap,
  incompatibleTypeMessage,
  isDatasetTypeCompatible,
  primaryKeyProperty,
  propertyMap,
  PROPERTY_DATATYPE_LABELS,
  supportsPrimaryKey,
} from './mappingTypes'

function issue(
  code: string,
  message: string,
  attributeId: string | null = null,
  column: string | null = null,
): MappingIssue {
  return { code, message, attribute_id: attributeId, column }
}

export function dedupeIssues(issues: MappingIssue[]): MappingIssue[] {
  const seen = new Set<string>()
  return issues.filter((item) => {
    const key = `${item.code}:${item.attribute_id ?? ''}:${item.column ?? ''}`
    if (seen.has(key)) return false
    seen.add(key)
    return true
  })
}

export function localObjectIssues(
  mapping: ObjectMapping,
  definition: OntologyResourceDefinition,
  columns: DatasetColumn[],
): { blocking: MappingIssue[]; warnings: MappingIssue[] } {
  const blocking: MappingIssue[] = []
  const warnings: MappingIssue[] = []
  const properties = propertyMap(definition)
  const columnsByName = columnMap(columns)
  const mappedAttributes = new Set<string>()
  const mappedColumns = new Set<string>()

  for (const attribute of mapping.attributes) {
    const property = properties.get(attribute.attribute_id)
    const column = columnsByName.get(attribute.column)
    if (!property) {
      blocking.push(issue('ATTRIBUTE_NOT_FOUND', '本体属性不存在', attribute.attribute_id, attribute.column))
      continue
    }
    if (property.is_array) {
      blocking.push(issue('ARRAY_UNSUPPORTED', '数组属性暂不支持映射', property.id, attribute.column))
    }
    if (!column) {
      blocking.push(issue('COLUMN_NOT_FOUND', '数据集字段不存在', property.id, attribute.column))
    } else if (!isDatasetTypeCompatible(property.datatype, column.type)) {
      blocking.push(issue('TYPE_INCOMPATIBLE', incompatibleTypeMessage(property, column), property.id, column.name))
    }
    if (mappedAttributes.has(property.id)) {
      blocking.push(issue('ATTRIBUTE_ALREADY_MAPPED', '同一属性被重复映射', property.id, attribute.column))
    }
    if (mappedColumns.has(attribute.column)) {
      blocking.push(issue('COLUMN_ALREADY_MAPPED', '同一字段被重复映射', property.id, attribute.column))
    }
    mappedAttributes.add(property.id)
    mappedColumns.add(attribute.column)
  }

  const primary = properties.get(mapping.primary_key_attribute_id)
  if (!primary || !supportsPrimaryKey(primary)) {
    blocking.push(issue('PRIMARY_KEY_NOT_FOUND', '主键属性不存在或类型不受支持', primary?.id ?? null))
  } else if (!mappedAttributes.has(primary.id)) {
    blocking.push(issue('PRIMARY_KEY_NOT_MAPPED', '主键属性未映射', primary.id))
  }

  for (const property of definition.properties) {
    if (mappedAttributes.has(property.id)) continue
    if (property.required) {
      blocking.push(issue('REQUIRED_ATTRIBUTE_UNMAPPED', '必填属性未映射', property.id))
    } else {
      warnings.push(issue('OPTIONAL_ATTRIBUTE_UNMAPPED', '可选属性未映射，生成时取空', property.id))
    }
  }
  const titleMapped = mapping.title_key_attribute_id
    ? mappedAttributes.has(mapping.title_key_attribute_id)
    : false
  if (!titleMapped) {
    warnings.push(issue('TITLE_FALLBACK', '标题为空时回退主键', mapping.title_key_attribute_id))
  }
  return { blocking, warnings }
}

export function localRelationIssues(
  mapping: RelationMapping,
  definition: OntologyResourceDefinition,
  columns: DatasetColumn[],
): { blocking: MappingIssue[]; warnings: MappingIssue[] } {
  const blocking: MappingIssue[] = []
  const columnsByName = columnMap(columns)
  const fromType = primaryKeyProperty(definition.source)?.datatype
  const toType = primaryKeyProperty(definition.target)?.datatype
  if (!fromType) blocking.push(issue('SOURCE_PRIMARY_KEY_MISSING', '源对象类型缺少主键'))
  if (!toType) blocking.push(issue('TARGET_PRIMARY_KEY_MISSING', '目标对象类型缺少主键'))
  if (!mapping.from_key_column) blocking.push(issue('FROM_KEY_NOT_MAPPED', '源端主键未映射'))
  if (!mapping.to_key_column) blocking.push(issue('TO_KEY_NOT_MAPPED', '目标端主键未映射'))
  if (mapping.from_key_column && mapping.from_key_column === mapping.to_key_column) {
    blocking.push(issue('RELATION_COLUMNS_NOT_DISTINCT', '两端主键不能使用同一个字段'))
  }
  for (const [columnName, datatype, label] of [
    [mapping.from_key_column, fromType, '源端'],
    [mapping.to_key_column, toType, '目标端'],
  ] as const) {
    if (!columnName || !datatype) continue
    const column = columnsByName.get(columnName)
    if (!column) {
      blocking.push(issue('COLUMN_NOT_FOUND', `${label}字段不存在`, null, columnName))
    } else if (!isDatasetTypeCompatible(datatype, column.type)) {
      blocking.push(issue(
        'TYPE_INCOMPATIBLE',
        `${label}主键类型 ${PROPERTY_DATATYPE_LABELS[datatype]}不能映射到 ${column.type} 字段`,
        null,
        columnName,
      ))
    }
  }
  return { blocking, warnings: [] }
}
