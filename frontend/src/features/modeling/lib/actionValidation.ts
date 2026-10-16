/**
 * 动作草稿的实时校验：一次算出「哪一行有问题」与「第一条待解决的问题」。
 *
 * 判断对齐后端的不变量，但多看一眼草稿本身：草稿里删掉一个参数、改掉它的类型，
 * 或本体那边删掉一个属性，都会让规则 / 赋值的引用失效——这些正是后端会拒的情况，
 * 界面上要当场标红并禁用保存，而不是等提交后报错。
 */

import type { GraphSnapshot } from '../../../shared/types'
import { ACTION_COPY, operatorOption, operatorsForDatatype, ruleKindOption } from './actionConfig'
import type {
  ActionAssignmentDraft,
  ActionConditionDraft,
  ActionDraft,
  ActionOperandDraft,
  ActionRuleDraft,
} from './actionDraft'
import { ENUM_DATATYPE, hasUsableEnumValues, sameEnumValues } from './datatypeOptions'
import { objectTypeName, parameterOf, propertyOf, relationPair } from './actionOptions'
import { parseStaticValue } from './staticValue'

/** 草稿里每一项的问题。`rows` 的键是草稿元素的 key，动作名本身用 `name`。 */
export interface ActionDraftIssues {
  rows: Record<string, string>
  /** 第一条待解决的问题；null 表示可以保存。 */
  first: string | null
}

export function actionDraftIssues(
  draft: ActionDraft,
  snapshot: GraphSnapshot | null,
): ActionDraftIssues {
  const rows: Record<string, string> = {}
  let first: string | null = null
  const report = (key: string, message: string) => {
    rows[key] = message
    first ??= message
  }

  if (!draft.name.trim()) report('name', '动作名不能为空')

  const seenNames = new Set<string>()
  for (const parameter of draft.parameters) {
    const name = parameter.name.trim()
    if (!name) report(parameter.key, '参数名不能为空')
    else if (seenNames.has(name)) report(parameter.key, `参数名「${name}」重复了`)
    else seenNames.add(name)

    if (parameter.kind === 'object_ref') {
      if (!parameter.targetObjectTypeId) report(parameter.key, '对象参数要选一个对象类型')
      else if (!objectTypeName(snapshot, parameter.targetObjectTypeId)) {
        report(parameter.key, ACTION_COPY.referenceChanged)
      }
      continue
    }
    if (parameter.datatype === ENUM_DATATYPE && !hasUsableEnumValues(parameter.enumValues)) {
      report(parameter.key, `枚举参数「${name || '未命名'}」至少要有 2 个互不相同的取值`)
    }
    if (parameter.defaultValue.trim()) {
      const parsed = parseStaticValue(parameter.defaultValue, {
        datatype: parameter.datatype,
        isArray: parameter.isArray,
        enumValues: parameter.enumValues,
      })
      if (!parsed.ok) report(parameter.key, `默认值：${parsed.error}`)
    }
  }

  for (const rule of draft.rules) {
    validateRule(draft, rule, snapshot, report)
  }
  for (const condition of draft.conditions) {
    validateCondition(draft, condition, snapshot, report)
  }

  return { rows, first }
}

type Report = (key: string, message: string) => void

function validateRule(
  draft: ActionDraft,
  rule: ActionRuleDraft,
  snapshot: GraphSnapshot | null,
  report: Report,
): void {
  const option = ruleKindOption(rule.ruleKind)
  if (!option) {
    report(rule.key, `不认识的规则种类：${rule.ruleKind}`)
    return
  }

  if (!option.assignable && rule.assignments.length) {
    report(rule.key, `「${option.label}」不带字段赋值`)
  }
  const assigned = new Set<string>()
  for (const assignment of rule.assignments) {
    validateAssignment(draft, assignment, snapshot, assigned, report)
  }

  if (option.family === 'object') {
    if (!rule.targetObjectTypeId) {
      report(rule.key, '选这条规则作用的对象类型')
      return
    }
    if (!objectTypeName(snapshot, rule.targetObjectTypeId)) {
      report(rule.key, ACTION_COPY.referenceChanged)
      return
    }
    if (rule.ruleKind === 'create_object') return
    reportObjectParameter(draft, rule, rule.sourceParameterId, rule.targetObjectTypeId, report)
    return
  }

  if (!rule.targetRelationId) {
    report(rule.key, '选这条规则作用的关系')
    return
  }
  const relation = relationPair(snapshot, rule.targetRelationId)
  if (relation === null) {
    report(rule.key, ACTION_COPY.referenceChanged)
    return
  }
  reportObjectParameter(draft, rule, rule.sourceParameterId, relation.sourceTypeId, report)
  reportObjectParameter(draft, rule, rule.targetParameterId, relation.targetTypeId, report)
  if (rule.sourceParameterId && rule.sourceParameterId === rule.targetParameterId) {
    report(rule.key, '链接两端要用两个不同的对象参数')
  }
}

/** 对象参数要存在、要是对象参数、不能是多值，而且指向指定的对象类型。 */
function reportObjectParameter(
  draft: ActionDraft,
  rule: ActionRuleDraft,
  parameterId: string,
  objectTypeId: string,
  report: Report,
): void {
  if (!parameterId) {
    report(rule.key, '选这条规则要用的对象参数')
    return
  }
  const parameter = parameterOf(draft.parameters, parameterId)
  if (!parameter || parameter.kind !== 'object_ref') {
    report(rule.key, ACTION_COPY.referenceChanged)
    return
  }
  if (parameter.isArray) {
    report(rule.key, ACTION_COPY.arrayHint)
    return
  }
  if (parameter.targetObjectTypeId !== objectTypeId) {
    report(rule.key, `对象参数「${parameter.name}」指向的不是这个对象类型`)
  }
}

function validateAssignment(
  draft: ActionDraft,
  assignment: ActionAssignmentDraft,
  snapshot: GraphSnapshot | null,
  assigned: Set<string>,
  report: Report,
): void {
  if (!assignment.targetPropertyId) {
    report(assignment.key, '选要赋值的属性')
    return
  }
  const target = propertyOf(snapshot, assignment.targetPropertyId)
  if (!target) {
    report(assignment.key, ACTION_COPY.referenceChanged)
    return
  }
  const where = `属性「${target.property.name}」`
  if (assigned.has(assignment.targetPropertyId)) {
    report(assignment.key, `${where}在这条规则里被赋值了两次`)
  }
  assigned.add(assignment.targetPropertyId)
  const datatype = target.property.datatype
  const enumValues = target.property.enum_values ?? null

  if (assignment.valueSource === 'parameter') {
    const parameter = parameterOf(draft.parameters, assignment.valueParameterId)
    if (!assignment.valueParameterId) {
      report(assignment.key, `${where}：选一个值参数`)
      return
    }
    if (!parameter || parameter.kind !== 'value') {
      report(assignment.key, ACTION_COPY.referenceChanged)
      return
    }
    if (parameter.isArray) {
      report(assignment.key, ACTION_COPY.arrayHint)
      return
    }
    reportDatatypeMismatch(report, assignment.key, where, parameter, datatype, enumValues)
    return
  }

  if (assignment.valueSource === 'object_property') {
    const parameter = parameterOf(draft.parameters, assignment.objectParameterId)
    if (!assignment.objectParameterId || !assignment.objectPropertyId) {
      report(assignment.key, `${where}：选对象参数与它的属性`)
      return
    }
    if (!parameter || parameter.kind !== 'object_ref') {
      report(assignment.key, ACTION_COPY.referenceChanged)
      return
    }
    if (parameter.isArray) {
      report(assignment.key, ACTION_COPY.arrayHint)
      return
    }
    const source = propertyOf(snapshot, assignment.objectPropertyId)
    if (!source || source.objectTypeId !== parameter.targetObjectTypeId) {
      report(assignment.key, `${where}：选的属性不属于对象参数「${parameter.name}」的类型`)
      return
    }
    reportDatatypeMismatch(
      report,
      assignment.key,
      where,
      { datatype: source.property.datatype, enumValues: source.property.enum_values ?? null },
      datatype,
      enumValues,
    )
    return
  }

  const parsed = parseStaticValue(assignment.staticValue, {
    datatype,
    isArray: target.property.is_array,
    enumValues,
  })
  if (!parsed.ok) report(assignment.key, `${where}：${parsed.error}`)
}

interface TypedSource {
  datatype: string
  enumValues: string[] | null
}

function reportDatatypeMismatch(
  report: Report,
  key: string,
  where: string,
  source: TypedSource,
  datatype: string,
  enumValues: string[] | null,
): void {
  if (source.datatype !== datatype) {
    report(key, `${where}：来源是 ${source.datatype}，属性是 ${datatype}，类型对不上`)
    return
  }
  if (datatype === ENUM_DATATYPE && !sameEnumValues(source.enumValues, enumValues)) {
    report(key, `${where}：枚举候选值要和属性完全一致`)
  }
}

function validateCondition(
  draft: ActionDraft,
  condition: ActionConditionDraft,
  snapshot: GraphSnapshot | null,
  report: Report,
): void {
  const left = resolveOperand(draft, condition.left, snapshot, false)
  if (!left.ok) {
    report(condition.key, `左侧：${left.error}`)
    return
  }
  const option = operatorOption(condition.operator)
  if (!option) {
    report(condition.key, '选一个运算符')
    return
  }
  if (!operatorsForDatatype(left.datatype).some((item) => item.value === option.value)) {
    report(condition.key, `运算符「${option.label}」不适用于 ${left.datatype} 的左侧`)
    return
  }
  if (option.unary) return

  const right = resolveOperand(draft, condition.right, snapshot, true)
  if (!right.ok) {
    report(condition.key, `右侧：${right.error}`)
    return
  }
  if (right.isStatic) {
    const parsed = parseStaticValue(condition.right.staticValue, {
      datatype: left.datatype,
      enumValues: left.enumValues,
    })
    if (!parsed.ok) report(condition.key, `右侧：${parsed.error}`)
    return
  }
  if (right.datatype !== left.datatype) {
    report(condition.key, `两侧类型要对得上：左边是 ${left.datatype}，右边是 ${right.datatype}`)
    return
  }
  if (left.datatype === ENUM_DATATYPE && !sameEnumValues(left.enumValues, right.enumValues)) {
    report(condition.key, '两侧的枚举候选值要一致')
  }
}

type OperandResolution =
  | { ok: false; error: string }
  /** 固定值站右边时 `isStatic` 为真：它的类型用不上，`datatype` 是空串。 */
  | { ok: true; isStatic: boolean; datatype: string; enumValues: string[] | null }

export type OperandDatatype =
  | { ok: true; datatype: string; enumValues: string[] | null }
  | { ok: false; error: string }

/**
 * 参数 / 对象属性这一侧自带的数据类型与候选值。固定值没有类型，这里不入这条路，
 * 它的类型由对面那一侧说了算。
 */
export function resolveOperandDatatype(
  draft: ActionDraft,
  operand: ActionOperandDraft,
  snapshot: GraphSnapshot | null,
): OperandDatatype {
  if (operand.kind === 'parameter') {
    const parameter = parameterOf(draft.parameters, operand.valueParameterId)
    if (!parameter) return { ok: false, error: ACTION_COPY.referenceChanged }
    if (parameter.kind !== 'value') return { ok: false, error: '这里要用值参数' }
    if (parameter.isArray) return { ok: false, error: '多值参数不能直接比较' }
    return { ok: true, datatype: parameter.datatype, enumValues: parameter.enumValues }
  }
  if (operand.kind !== 'object_property') {
    return { ok: false, error: '固定值没有类型，请放在右侧' }
  }
  const parameter = parameterOf(draft.parameters, operand.objectParameterId)
  if (!parameter || parameter.kind !== 'object_ref') {
    return { ok: false, error: ACTION_COPY.referenceChanged }
  }
  const source = propertyOf(snapshot, operand.objectPropertyId)
  if (!source || source.objectTypeId !== parameter.targetObjectTypeId) {
    return { ok: false, error: `选的属性不属于对象参数「${parameter.name}」的类型` }
  }
  if (source.property.is_array) return { ok: false, error: '多值属性还不能参与校验' }
  return {
    ok: true,
    datatype: source.property.datatype,
    enumValues: source.property.enum_values ?? null,
  }
}

/**
 * 一侧操作数能不能站得住。参数与对象属性自带类型；固定值没有类型，
 * 只能站右边，类型由左边说了算——`allowStatic` 就是这道门。
 */
function resolveOperand(
  draft: ActionDraft,
  operand: ActionOperandDraft,
  snapshot: GraphSnapshot | null,
  allowStatic: boolean,
): OperandResolution {
  if (operand.kind === 'static') {
    if (!allowStatic) return { ok: false, error: '左侧不能放固定值' }
    if (!operand.staticValue.trim()) return { ok: false, error: '固定值不能为空' }
    return { ok: true, isStatic: true, datatype: '', enumValues: null }
  }

  const info = resolveOperandDatatype(draft, operand, snapshot)
  if (!info.ok) return { ok: false, error: info.error }
  return { ok: true, isStatic: false, datatype: info.datatype, enumValues: info.enumValues }
}
