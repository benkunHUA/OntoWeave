import type {
  DatasetColumn,
  ObjectMapping,
  OntologyProperty,
  PropertyDatatype,
  RelationMapping,
} from '../../../shared/types'
import {
  incompatibleTypeMessage,
  isDatasetTypeCompatible,
  mappingAttribute,
  PROPERTY_DATATYPE_LABELS,
  type RelationEndpoint,
} from './mappingTypes'

export type MappingConnection =
  | { ok: true; mapping: ObjectMapping | RelationMapping }
  | { ok: false; code: string; message: string }

export function normalizeConnectionHandles(
  sourceHandle: string | null,
  targetHandle: string | null,
): { left: string; column: string } | null {
  if (!sourceHandle || !targetHandle) return null
  if (sourceHandle.startsWith('column:')) {
    return { left: targetHandle, column: sourceHandle.slice('column:'.length) }
  }
  if (targetHandle.startsWith('column:')) {
    return { left: sourceHandle, column: targetHandle.slice('column:'.length) }
  }
  return null
}

export function connectObjectAttribute(
  mapping: ObjectMapping,
  property: OntologyProperty,
  column: DatasetColumn,
): MappingConnection {
  if (property.is_array) {
    return { ok: false, code: 'ARRAY_UNSUPPORTED', message: '数组属性暂不支持映射' }
  }
  if (mapping.attributes.some((item) => item.attribute_id === property.id)) {
    return { ok: false, code: 'ATTRIBUTE_OCCUPIED', message: '该属性已经映射，请先删除原连线' }
  }
  if (mapping.attributes.some((item) => item.column === column.name)) {
    return { ok: false, code: 'COLUMN_OCCUPIED', message: '该字段已经映射，请先删除原连线' }
  }
  if (!isDatasetTypeCompatible(property.datatype, column.type)) {
    return { ok: false, code: 'TYPE_INCOMPATIBLE', message: incompatibleTypeMessage(property, column) }
  }
  return {
    ok: true,
    mapping: {
      ...mapping,
      attributes: [...mapping.attributes, mappingAttribute(property, column.name)],
      required_attribute_ids: mapping.required_attribute_ids,
    },
  }
}

export function removeObjectConnection(mapping: ObjectMapping, edgeId: string): ObjectMapping {
  const attributeId = edgeId.startsWith('object:') ? edgeId.slice('object:'.length) : ''
  return {
    ...mapping,
    attributes: mapping.attributes.filter((item) => item.attribute_id !== attributeId),
  }
}

export function connectRelationEndpoint(
  mapping: RelationMapping,
  endpoint: RelationEndpoint,
  endpointDatatype: PropertyDatatype,
  column: DatasetColumn,
): MappingConnection {
  const existingColumn = endpoint === 'from' ? mapping.from_key_column : mapping.to_key_column
  const otherColumn = endpoint === 'from' ? mapping.to_key_column : mapping.from_key_column
  if (existingColumn) {
    return { ok: false, code: 'ENDPOINT_OCCUPIED', message: '该端主键已映射，请先删除原连线' }
  }
  if (otherColumn && otherColumn === column.name) {
    return { ok: false, code: 'COLUMN_OCCUPIED', message: '两端主键不能使用同一个字段' }
  }
  if (!isDatasetTypeCompatible(endpointDatatype, column.type)) {
    return {
      ok: false,
      code: 'TYPE_INCOMPATIBLE',
      message: `主键类型 ${PROPERTY_DATATYPE_LABELS[endpointDatatype]}不能映射到 ${column.type} 字段`,
    }
  }
  return {
    ok: true,
    mapping: endpoint === 'from'
      ? { ...mapping, from_key_column: column.name, from_key_datatype: endpointDatatype }
      : { ...mapping, to_key_column: column.name, to_key_datatype: endpointDatatype },
  }
}

export function removeRelationConnection(
  mapping: RelationMapping,
  endpoint: RelationEndpoint,
): RelationMapping {
  return endpoint === 'from'
    ? { ...mapping, from_key_column: '' }
    : { ...mapping, to_key_column: '' }
}
