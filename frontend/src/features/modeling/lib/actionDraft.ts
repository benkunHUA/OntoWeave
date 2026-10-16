/**
 * 动作抽屉的草稿模型：把动作详情摊成「可编辑的一份定义」。
 *
 * 已有元素带着后端 id（`key` 也用它），新增元素带临时 id；保存时由 `actionEdits`
 * 比对出编辑清单，实时校验由 `actionValidation` 负责。
 */

import type {
  ActionCriteriaNodeDetail,
  ActionDetail,
  ActionOperandDetail,
} from '../../../shared/types'
import { staticValueText } from './staticValue'

export interface ActionParameterDraft {
  /** React 列表用的稳定 key：已有参数用后端 id，新增参数用临时 id。 */
  key: string
  /** 后端 id；null 表示这次新增、还没保存，暂时不能被规则引用。 */
  id: string | null
  name: string
  description: string
  kind: string
  /** kind = value 时的数据类型。 */
  datatype: string
  /** kind = object_ref 时指向的对象类型。 */
  targetObjectTypeId: string
  isArray: boolean
  isRequired: boolean
  /** kind = value 且 datatype = enum 时的候选值。 */
  enumValues: string[]
  /** 默认值的输入文本；空串表示不设默认值。 */
  defaultValue: string
}

export interface ActionAssignmentDraft {
  key: string
  /** 后端 id；null 表示还没保存（新规则里的赋值随规则一起建）。 */
  id: string | null
  targetPropertyId: string
  valueSource: string
  valueParameterId: string
  objectParameterId: string
  objectPropertyId: string
  staticValue: string
}

export interface ActionRuleDraft {
  key: string
  id: string | null
  ruleKind: string
  /** 对象规则作用的对象类型。 */
  targetObjectTypeId: string
  /** 链接规则作用的多对多关系。 */
  targetRelationId: string
  sourceParameterId: string
  targetParameterId: string
  assignments: ActionAssignmentDraft[]
}

export interface ActionOperandDraft {
  kind: string
  valueParameterId: string
  objectParameterId: string
  objectPropertyId: string
  staticValue: string
}

export interface ActionConditionDraft {
  key: string
  operator: string
  failureMessage: string
  left: ActionOperandDraft
  right: ActionOperandDraft
}

export interface ActionDraft {
  name: string
  description: string
  parameters: ActionParameterDraft[]
  rules: ActionRuleDraft[]
  conditions: ActionConditionDraft[]
  /** 被删掉的已有参数 / 规则 id，保存时转成 delete 操作。 */
  removedParameterIds: string[]
  removedRuleIds: string[]
}

/**
 * 详情落成草稿。已在改动集里标删、还没确认的子元素先排除掉：
 * 库里它们还在，详情照样会返回，但界面上不该再当成可编辑的草稿。
 */
export function draftFromActionDetail(
  detail: ActionDetail,
  pendingDeleteIds?: ReadonlySet<string>,
): ActionDraft {
  const keep = (id: string) => !pendingDeleteIds?.has(id)
  return {
    name: detail.name,
    description: detail.description ?? '',
    parameters: detail.parameters.filter((parameter) => keep(parameter.id)).map((parameter) => ({
      key: parameter.id,
      id: parameter.id,
      name: parameter.name,
      description: parameter.description ?? '',
      kind: parameter.kind,
      datatype: parameter.datatype ?? 'text',
      targetObjectTypeId: parameter.target_object_type_id ?? '',
      isArray: parameter.is_array,
      isRequired: parameter.is_required,
      enumValues: parameter.enum_values ?? [],
      defaultValue: staticValueText(parameter.default_value),
    })),
    rules: detail.rules.filter((rule) => keep(rule.id)).map((rule) => ({
      key: rule.id,
      id: rule.id,
      ruleKind: rule.rule_kind,
      targetObjectTypeId: rule.target_object_type_id ?? '',
      targetRelationId: rule.target_relation_id ?? '',
      sourceParameterId: rule.source_parameter_id ?? '',
      targetParameterId: rule.target_parameter_id ?? '',
      assignments: rule.assignments.map((assignment) => ({
        key: assignment.id,
        id: assignment.id,
        targetPropertyId: assignment.target_property_id,
        valueSource: assignment.value_source,
        valueParameterId: assignment.value_parameter_id ?? '',
        objectParameterId: assignment.object_parameter_id ?? '',
        objectPropertyId: assignment.object_property_id ?? '',
        staticValue: staticValueText(assignment.static_value),
      })),
    })),
    conditions: flattenConditions(detail.criteria)
      .filter((node) => keep(node.id))
      .map((node) => ({
        key: node.id,
        operator: node.operator ?? '',
        failureMessage: node.failure_message ?? '',
        left: operandDraft(node.left),
        right: operandDraft(node.right),
      })),
    removedParameterIds: [],
    removedRuleIds: [],
  }
}

export function newParameterDraft(): ActionParameterDraft {
  return {
    key: crypto.randomUUID(),
    id: null,
    name: '',
    description: '',
    kind: 'value',
    datatype: 'text',
    targetObjectTypeId: '',
    isArray: false,
    isRequired: false,
    enumValues: [],
    defaultValue: '',
  }
}

export function newRuleDraft(ruleKind: string): ActionRuleDraft {
  return {
    key: crypto.randomUUID(),
    id: null,
    ruleKind,
    targetObjectTypeId: '',
    targetRelationId: '',
    sourceParameterId: '',
    targetParameterId: '',
    assignments: [],
  }
}

export function newAssignmentDraft(): ActionAssignmentDraft {
  return {
    key: crypto.randomUUID(),
    id: null,
    targetPropertyId: '',
    valueSource: 'parameter',
    valueParameterId: '',
    objectParameterId: '',
    objectPropertyId: '',
    staticValue: '',
  }
}

export function newConditionDraft(): ActionConditionDraft {
  return {
    key: crypto.randomUUID(),
    operator: 'is not null',
    failureMessage: '',
    left: emptyOperandDraft('parameter'),
    right: emptyOperandDraft('static'),
  }
}

export function emptyOperandDraft(kind: string): ActionOperandDraft {
  return {
    kind,
    valueParameterId: '',
    objectParameterId: '',
    objectPropertyId: '',
    staticValue: '',
  }
}

/** 库里读出的条件树拍平成一条条条件：v1 只有一个 `and` 根，分组不参与编辑。 */
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

/** 这个动作自己已经标删、还没确认的子元素：抽屉靠它把行收起来并给出提示。 */
export function actionPendingDeleteIds(
  detail: ActionDetail,
  deleted: ReadonlySet<string>,
): Set<string> {
  const own = new Set<string>()
  for (const parameter of detail.parameters) {
    if (deleted.has(parameter.id)) own.add(parameter.id)
  }
  for (const rule of detail.rules) {
    if (deleted.has(rule.id)) own.add(rule.id)
  }
  for (const node of flattenConditions(detail.criteria)) {
    if (deleted.has(node.id)) own.add(node.id)
  }
  return own
}

/** 详情里的操作数落成草稿；没有右侧（判空类运算符）时给一份空的静态操作数。 */
function operandDraft(operand: ActionOperandDetail | null): ActionOperandDraft {
  if (!operand) return emptyOperandDraft('static')
  return {
    kind: operand.kind,
    valueParameterId: operand.value_parameter_id ?? '',
    objectParameterId: operand.object_parameter_id ?? '',
    objectPropertyId: operand.object_property_id ?? '',
    staticValue: staticValueText(operand.static_value),
  }
}
