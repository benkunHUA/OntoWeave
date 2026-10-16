/**
 * 动作抽屉里的候选项与查找：所有下拉（对象类型、关系、属性、参数）都从这里取，
 * 保证「能选什么」只有一份判断——规则、赋值、校验三处引用的都是同一批元素。
 */

import type { GraphProperty, GraphSnapshot } from '../../../shared/types'
import type { ActionParameterDraft } from './actionDraft'

export interface SelectOption {
  value: string
  label: string
}

/** 只有多对多关系用链接规则；一对一 / 一对多改外键属性，走修改对象。 */
const LINK_CARDINALITY = 'many_to_many'

export function objectTypeOptions(snapshot: GraphSnapshot | null): SelectOption[] {
  return (snapshot?.nodes ?? []).map((node) => ({ value: node.id, label: node.name }))
}

export function relationOptions(snapshot: GraphSnapshot | null): SelectOption[] {
  return (snapshot?.relations ?? [])
    .filter((relation) => relation.cardinality === LINK_CARDINALITY)
    .map((relation) => ({
      value: relation.id,
      label: `${relation.name}（${typeName(snapshot, relation.source_type_id)} → ${typeName(snapshot, relation.target_type_id)}）`,
    }))
}

export function propertyOptions(
  snapshot: GraphSnapshot | null,
  objectTypeId: string,
): SelectOption[] {
  return propertiesOf(snapshot, objectTypeId).map((property) => ({
    value: property.id,
    label: property.name,
  }))
}

export function propertiesOf(
  snapshot: GraphSnapshot | null,
  objectTypeId: string,
): GraphProperty[] {
  if (!objectTypeId) return []
  return snapshot?.nodes.find((node) => node.id === objectTypeId)?.properties ?? []
}

export interface PropertyLookup {
  property: GraphProperty
  objectTypeId: string
}

/** 按属性 id 反查属性与它所属的对象类型；引用的元素被删掉时返回 null。 */
export function propertyOf(
  snapshot: GraphSnapshot | null,
  propertyId: string,
): PropertyLookup | null {
  if (!propertyId) return null
  for (const node of snapshot?.nodes ?? []) {
    const property = node.properties.find((item) => item.id === propertyId)
    if (property) return { property, objectTypeId: node.id }
  }
  return null
}

export function objectTypeName(snapshot: GraphSnapshot | null, objectTypeId: string): string | null {
  return snapshot?.nodes.find((node) => node.id === objectTypeId)?.name ?? null
}

export function relationPair(
  snapshot: GraphSnapshot | null,
  relationId: string,
): { name: string; sourceTypeId: string; targetTypeId: string } | null {
  const relation = snapshot?.relations.find((item) => item.id === relationId)
  if (!relation) return null
  return {
    name: relation.name,
    sourceTypeId: relation.source_type_id,
    targetTypeId: relation.target_type_id,
  }
}

/** 可以喂给字段的值参数：多值参数暂时喂不了单值，所以先不放进来。 */
export function valueParameterOptions(parameters: ActionParameterDraft[]): SelectOption[] {
  return savedParameters(parameters)
    .filter((parameter) => parameter.kind === 'value' && !parameter.isArray)
    .map((parameter) => ({ value: parameter.id as string, label: parameter.name }))
}

/**
 * 可以当对象用的参数。给定 `objectTypeId` 时只留指向这个类型的参数——
 * 链接规则两端、修改 / 删除对象的对象参数都必须对得上类型。
 */
export function objectParameterOptions(
  parameters: ActionParameterDraft[],
  snapshot: GraphSnapshot | null,
  objectTypeId?: string,
): SelectOption[] {
  return savedParameters(parameters)
    .filter((parameter) => parameter.kind === 'object_ref' && !parameter.isArray)
    .filter((parameter) => !objectTypeId || parameter.targetObjectTypeId === objectTypeId)
    .map((parameter) => {
      const typeName = objectTypeName(snapshot, parameter.targetObjectTypeId)
      return {
        value: parameter.id as string,
        label: typeName ? `${parameter.name}（${typeName}）` : parameter.name,
      }
    })
}

export function parameterOf(
  parameters: ActionParameterDraft[],
  parameterId: string,
): ActionParameterDraft | null {
  return savedParameters(parameters).find((parameter) => parameter.id === parameterId) ?? null
}

/** 只有已保存的参数才有 id，才能被规则与校验引用。 */
function savedParameters(parameters: ActionParameterDraft[]): ActionParameterDraft[] {
  return parameters.filter((parameter) => parameter.id !== null)
}

function typeName(snapshot: GraphSnapshot | null, typeId: string): string {
  return objectTypeName(snapshot, typeId) ?? '未知对象'
}
