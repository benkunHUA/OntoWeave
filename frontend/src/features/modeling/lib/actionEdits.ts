/**
 * 动作草稿 → 编辑清单：只提交真正变了的元素与字段。
 *
 * 三种落法：改已有元素走 `update`（只带变了的列）、新增走各自的 create op、
 * 删除走 `delete`；提交校验比较特殊——后端整棵替换，所以条件列表变了才发一条
 * `set_action_criteria`，不逐条 diff。
 */

import type {
  ActionAssignmentDetail,
  ActionAssignmentIn,
  ActionConditionIn,
  ActionCriteriaNodeDetail,
  ActionDetail,
  ActionOperandDetail,
  ActionOperandIn,
  ActionParameterCreateEdit,
  ActionParameterDetail,
  ActionRuleCreateEdit,
  ActionRuleDetail,
  EditPatchValue,
  GraphEditOp,
  GraphSnapshot,
} from '../../../shared/types'
import { operatorOption, ruleKindOption } from './actionConfig'
import type {
  ActionAssignmentDraft,
  ActionConditionDraft,
  ActionDraft,
  ActionOperandDraft,
  ActionParameterDraft,
  ActionRuleDraft,
} from './actionDraft'
import { enumValuesFor, sameEnumValues } from './datatypeOptions'
import { propertyOf } from './actionOptions'
import { parseStaticValue } from './staticValue'
import { resolveOperandDatatype } from './actionValidation'

/** 参数的形态列：改 kind / datatype 会牵动其它几列，得整组一起发。 */
const PARAMETER_SHAPE_KEYS = [
  'kind',
  'datatype',
  'target_object_type_id',
  'enum_values',
  'is_array',
  'default_value',
] as const

type ParameterShapeKey = (typeof PARAMETER_SHAPE_KEYS)[number]

export function buildActionEdits(
  detail: ActionDetail,
  draft: ActionDraft,
  snapshot: GraphSnapshot | null,
): GraphEditOp[] {
  const edits: GraphEditOp[] = []
  const basics = basicsEdit(detail, draft)
  if (basics) edits.push(basics)

  const savedParameters = new Map(detail.parameters.map((item) => [item.id, item]))
  for (const parameter of draft.parameters) {
    if (parameter.id === null) {
      edits.push(parameterCreateEdit(detail.id, parameter))
      continue
    }
    const original = savedParameters.get(parameter.id)
    if (!original) continue
    const patch = parameterPatch(original, parameter)
    if (Object.keys(patch).length) {
      edits.push({
        op: 'update',
        element_kind: 'action_parameter',
        element_id: parameter.id,
        patch,
      })
    }
  }
  for (const id of draft.removedParameterIds) {
    edits.push({ op: 'delete', element_kind: 'action_parameter', element_id: id })
  }

  const savedRules = new Map(detail.rules.map((item) => [item.id, item]))
  for (const rule of draft.rules) {
    if (rule.id === null) {
      edits.push(ruleCreateEdit(detail.id, rule, snapshot))
      continue
    }
    const original = savedRules.get(rule.id)
    if (!original) continue
    edits.push(...ruleEdits(rule, original, snapshot))
  }
  for (const id of draft.removedRuleIds) {
    edits.push({ op: 'delete', element_kind: 'action_rule', element_id: id })
  }

  const criteria = criteriaEdit(detail, draft, snapshot)
  if (criteria) edits.push(criteria)

  return edits
}

export function isActionDraftDirty(
  detail: ActionDetail,
  draft: ActionDraft,
  snapshot: GraphSnapshot | null,
): boolean {
  return buildActionEdits(detail, draft, snapshot).length > 0
}

function basicsEdit(detail: ActionDetail, draft: ActionDraft): GraphEditOp | null {
  const patch: Record<string, EditPatchValue> = {}
  const name = draft.name.trim()
  if (name !== detail.name) patch.name = name
  const description = normalizeText(draft.description)
  if (description !== detail.description) patch.description = description
  if (!Object.keys(patch).length) return null
  return { op: 'update', element_kind: 'action_type', element_id: detail.id, patch }
}

function parameterCreateEdit(
  actionTypeId: string,
  parameter: ActionParameterDraft,
): ActionParameterCreateEdit {
  return {
    op: 'add_action_parameter',
    action_type_id: actionTypeId,
    name: parameter.name.trim(),
    description: normalizeText(parameter.description),
    is_required: parameter.isRequired,
    ...parameterShape(parameter),
  }
}

function parameterPatch(
  original: ActionParameterDetail,
  parameter: ActionParameterDraft,
): Record<string, EditPatchValue> {
  const patch: Record<string, EditPatchValue> = {}
  const name = parameter.name.trim()
  if (name !== original.name) patch.name = name
  const description = normalizeText(parameter.description)
  if (description !== original.description) patch.description = description
  if (parameter.isRequired !== original.is_required) patch.is_required = parameter.isRequired

  const shape = parameterShape(parameter)
  const originalShape: Record<ParameterShapeKey, unknown> = {
    kind: original.kind,
    datatype: original.datatype,
    target_object_type_id: original.target_object_type_id,
    enum_values: original.enum_values,
    is_array: original.is_array,
    default_value: original.default_value ?? null,
  }
  const changed = PARAMETER_SHAPE_KEYS.filter(
    (key) => !sameValue(shape[key], originalShape[key]),
  )
  if (!changed.length) return patch
  // 换 kind / datatype 时另外几列不再成立，整组一起发，避免后端读到半新半旧的组合。
  const keys: readonly ParameterShapeKey[] =
    changed.includes('kind') || changed.includes('datatype') ? PARAMETER_SHAPE_KEYS : changed
  for (const key of keys) patch[key] = shape[key] as EditPatchValue
  return patch
}

interface ParameterShape {
  kind: string
  datatype: string | null
  target_object_type_id: string | null
  enum_values: string[] | null
  default_value: unknown
  is_array: boolean
}

/** 参数的两组列互斥：值参数带类型与取值，对象参数带目标类型。 */
function parameterShape(parameter: ActionParameterDraft): ParameterShape {
  const isValue = parameter.kind === 'value'
  return {
    kind: parameter.kind,
    datatype: isValue ? parameter.datatype : null,
    target_object_type_id: isValue ? null : parameter.targetObjectTypeId,
    enum_values: isValue ? enumValuesFor(parameter.datatype, parameter.enumValues) : null,
    default_value: isValue ? optionalStaticValue(parameter) : null,
    is_array: parameter.isArray,
  }
}

function optionalStaticValue(parameter: ActionParameterDraft): unknown {
  if (!parameter.defaultValue.trim()) return null
  const parsed = parseStaticValue(parameter.defaultValue, {
    datatype: parameter.datatype,
    isArray: parameter.isArray,
    enumValues: parameter.enumValues,
  })
  return parsed.ok ? parsed.value : null
}

function ruleCreateEdit(
  actionTypeId: string,
  rule: ActionRuleDraft,
  snapshot: GraphSnapshot | null,
): ActionRuleCreateEdit {
  const option = ruleKindOption(rule.ruleKind)
  const base = {
    op: 'add_action_rule',
    action_type_id: actionTypeId,
    rule_kind: rule.ruleKind,
  } as const
  if (option?.family === 'object') {
    return {
      ...base,
      target_object_type_id: rule.targetObjectTypeId,
      source_parameter_id: rule.ruleKind === 'create_object' ? null : rule.sourceParameterId,
      assignments: option.assignable
        ? rule.assignments.map((assignment) => assignmentIn(assignment, snapshot))
        : [],
    }
  }
  return {
    ...base,
    target_relation_id: rule.targetRelationId,
    source_parameter_id: rule.sourceParameterId,
    target_parameter_id: rule.targetParameterId,
  }
}

/** 已存在的规则：只改作用对象与两端参数，赋值另走 add / update / delete。 */
function ruleEdits(
  rule: ActionRuleDraft,
  original: ActionRuleDetail,
  snapshot: GraphSnapshot | null,
): GraphEditOp[] {
  const edits: GraphEditOp[] = []
  const ruleId = original.id
  const patch: Record<string, EditPatchValue> = {}
  const columns: Array<[string, string | null, string | null]> = [
    ['target_object_type_id', rule.targetObjectTypeId || null, original.target_object_type_id],
    ['target_relation_id', rule.targetRelationId || null, original.target_relation_id],
    ['source_parameter_id', rule.sourceParameterId || null, original.source_parameter_id],
    ['target_parameter_id', rule.targetParameterId || null, original.target_parameter_id],
  ]
  for (const [key, next, current] of columns) {
    if (next !== current) patch[key] = next
  }
  if (Object.keys(patch).length) {
    edits.push({ op: 'update', element_kind: 'action_rule', element_id: ruleId, patch })
  }

  const saved = new Map(original.assignments.map((item) => [item.id, item]))
  for (const assignment of rule.assignments) {
    const next = assignmentIn(assignment, snapshot)
    if (assignment.id === null) {
      edits.push({ op: 'add_action_rule_assignment', rule_id: ruleId, ...next })
      continue
    }
    const before = saved.get(assignment.id)
    if (!before) continue
    const assignmentPatch = changedAssignmentFields(before, next)
    if (Object.keys(assignmentPatch).length) {
      edits.push({
        op: 'update',
        element_kind: 'action_rule_assignment',
        element_id: assignment.id,
        patch: assignmentPatch,
      })
    }
  }
  const kept = new Set(rule.assignments.map((assignment) => assignment.id))
  for (const before of original.assignments) {
    if (kept.has(before.id)) continue
    edits.push({ op: 'delete', element_kind: 'action_rule_assignment', element_id: before.id })
  }
  return edits
}

const ASSIGNMENT_KEYS = [
  'target_property_id',
  'value_source',
  'value_parameter_id',
  'object_parameter_id',
  'object_property_id',
  'static_value',
] as const

function changedAssignmentFields(
  before: ActionAssignmentDetail,
  next: ActionAssignmentIn,
): Record<string, EditPatchValue> {
  const current: Record<string, unknown> = {
    target_property_id: before.target_property_id,
    value_source: before.value_source,
    value_parameter_id: before.value_parameter_id,
    object_parameter_id: before.object_parameter_id,
    object_property_id: before.object_property_id,
    static_value: before.static_value ?? null,
  }
  const patch: Record<string, EditPatchValue> = {}
  for (const key of ASSIGNMENT_KEYS) {
    const value = (next[key] ?? null) as EditPatchValue
    if (!sameValue(value, current[key])) patch[key] = value
  }
  return patch
}

function assignmentIn(
  assignment: ActionAssignmentDraft,
  snapshot: GraphSnapshot | null,
): ActionAssignmentIn {
  const base = {
    target_property_id: assignment.targetPropertyId,
    value_source: assignment.valueSource,
  }
  if (assignment.valueSource === 'parameter') {
    return { ...base, value_parameter_id: assignment.valueParameterId }
  }
  if (assignment.valueSource === 'object_property') {
    return {
      ...base,
      object_parameter_id: assignment.objectParameterId,
      object_property_id: assignment.objectPropertyId,
    }
  }
  return { ...base, static_value: parsedStaticValue(assignment, snapshot) }
}

/** 固定值要按目标属性的类型落库：文本进 text、5 进 integer、true 进 boolean。 */
function parsedStaticValue(
  assignment: ActionAssignmentDraft,
  snapshot: GraphSnapshot | null,
): unknown {
  const target = propertyOf(snapshot, assignment.targetPropertyId)
  if (!target) return null
  const parsed = parseStaticValue(assignment.staticValue, {
    datatype: target.property.datatype,
    isArray: target.property.is_array,
    enumValues: target.property.enum_values,
  })
  return parsed.ok ? parsed.value : null
}

/** 条件列表整棵比对：形状一致就不发，不一致才整棵替换。 */
function criteriaEdit(
  detail: ActionDetail,
  draft: ActionDraft,
  snapshot: GraphSnapshot | null,
): GraphEditOp | null {
  const next = draft.conditions.map((condition) => conditionIn(condition, draft, snapshot))
  const current = flattenConditions(detail.criteria).map(conditionInFromDetail)
  if (JSON.stringify(next) === JSON.stringify(current)) return null
  return { op: 'set_action_criteria', action_type_id: detail.id, conditions: next }
}

function flattenConditions(nodes: ActionCriteriaNodeDetail[]): ActionCriteriaNodeDetail[] {
  const conditions: ActionCriteriaNodeDetail[] = []
  const walk = (current: ActionCriteriaNodeDetail[]) => {
    for (const node of current) {
      if (node.node_kind === 'condition') conditions.push(node)
      else walk(node.children)
    }
  }
  walk(nodes)
  return conditions
}

function conditionIn(
  condition: ActionConditionDraft,
  draft: ActionDraft,
  snapshot: GraphSnapshot | null,
): ActionConditionIn {
  const unary = operatorOption(condition.operator)?.unary ?? false
  const left = resolveOperandDatatype(draft, condition.left, snapshot)
  return {
    operator: condition.operator,
    failure_message: normalizeText(condition.failureMessage),
    left: operandIn(condition.left, null),
    right: unary ? null : operandIn(condition.right, left.ok ? left : null),
  }
}

function conditionInFromDetail(node: ActionCriteriaNodeDetail): ActionConditionIn {
  return {
    operator: node.operator ?? '',
    failure_message: node.failure_message,
    left: operandInFromDetail(node.left),
    right: node.right ? operandInFromDetail(node.right) : null,
  }
}

/** 三列空值也要照发：整棵比对时两边形状必须一样，才比得出「没改」。 */
function operandIn(
  operand: ActionOperandDraft,
  counterpart: { datatype: string; enumValues: string[] | null } | null,
): ActionOperandIn {
  const empty = {
    value_parameter_id: null,
    object_parameter_id: null,
    object_property_id: null,
    static_value: null,
  }
  if (operand.kind === 'parameter') {
    return { kind: operand.kind, ...empty, value_parameter_id: operand.valueParameterId }
  }
  if (operand.kind === 'object_property') {
    return {
      kind: operand.kind,
      ...empty,
      object_parameter_id: operand.objectParameterId,
      object_property_id: operand.objectPropertyId,
    }
  }
  const parsed = counterpart
    ? parseStaticValue(operand.staticValue, {
        datatype: counterpart.datatype,
        enumValues: counterpart.enumValues,
      })
    : null
  return { kind: operand.kind, ...empty, static_value: parsed?.ok ? parsed.value : null }
}

function operandInFromDetail(operand: ActionOperandDetail | null): ActionOperandIn {
  if (!operand) {
    return {
      kind: 'static',
      value_parameter_id: null,
      object_parameter_id: null,
      object_property_id: null,
      static_value: null,
    }
  }
  return {
    kind: operand.kind,
    value_parameter_id: operand.value_parameter_id,
    object_parameter_id: operand.object_parameter_id,
    object_property_id: operand.object_property_id,
    static_value: operand.static_value ?? null,
  }
}

function normalizeText(value: string): string | null {
  const trimmed = value.trim()
  return trimmed ? trimmed : null
}

/** 比对用：数组按内容比，null / undefined 当同一件事。 */
function sameValue(left: unknown, right: unknown): boolean {
  if (left === null || left === undefined) return right === null || right === undefined
  if (Array.isArray(left) || Array.isArray(right)) {
    if (!Array.isArray(left) || !Array.isArray(right)) return false
    return sameEnumValues(left as string[], right as string[])
  }
  return left === right
}
