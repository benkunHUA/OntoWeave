/**
 * 动作界面用到的全部枚举与文案：规则种类、参数种类、值来源、运算符矩阵、区块标题。
 * 组件只渲染这里给出的选项，不自己写死取值与中文。
 */

import { ORDERED_DATATYPES } from './datatypeOptions'

/** 一条规则的作用对象：对象规则认对象类型，链接规则认一条多对多关系。 */
export type RuleFamily = 'object' | 'link'

export interface RuleKindOption {
  value: string
  label: string
  /** 这句话解释这条规则要配哪些参数，界面上直接显示。 */
  hint: string
  family: RuleFamily
  /** 只有新建对象 / 修改对象能带字段赋值。 */
  assignable: boolean
}

export const RULE_KIND_OPTIONS: RuleKindOption[] = [
  {
    value: 'create_object',
    label: '新建对象',
    hint: '按字段赋值造一个新对象，不需要对象参数。',
    family: 'object',
    assignable: true,
  },
  {
    value: 'modify_object',
    label: '修改对象',
    hint: '改一个已有对象的字段，选它的对象参数。',
    family: 'object',
    assignable: true,
  },
  {
    value: 'delete_object',
    label: '删除对象',
    hint: '删掉一个已有对象，选它的对象参数。',
    family: 'object',
    assignable: false,
  },
  {
    value: 'create_link',
    label: '新建链接',
    hint: '在两个对象之间连一条多对多链接，两端各选一个对象参数。',
    family: 'link',
    assignable: false,
  },
  {
    value: 'delete_link',
    label: '删除链接',
    hint: '断开两个对象之间的多对多链接，两端各选一个对象参数。',
    family: 'link',
    assignable: false,
  },
]

export function ruleKindOption(value: string): RuleKindOption | null {
  return RULE_KIND_OPTIONS.find((option) => option.value === value) ?? null
}

export function ruleKindLabel(value: string): string {
  return ruleKindOption(value)?.label ?? value
}

export interface ParameterKindOption {
  value: string
  label: string
  hint: string
}

export const PARAMETER_KIND_OPTIONS: ParameterKindOption[] = [
  {
    value: 'value',
    label: '值参数',
    hint: '文本、数字、日期或枚举取值，按属性数据类型走。',
  },
  {
    value: 'object_ref',
    label: '对象参数',
    hint: '指向某个对象类型的一个实例，规则靠它认对象。',
  },
]

export function parameterKindLabel(value: string): string {
  return PARAMETER_KIND_OPTIONS.find((option) => option.value === value)?.label ?? value
}

/** 赋值与条件操作数的取值形态：三种，取值即后端枚举。 */
export interface ValueSourceOption {
  value: string
  label: string
}

export const VALUE_SOURCE_OPTIONS: ValueSourceOption[] = [
  { value: 'parameter', label: '动作参数' },
  { value: 'object_property', label: '对象参数的属性' },
  { value: 'static', label: '固定值' },
]

export function valueSourceLabel(value: string): string {
  return VALUE_SOURCE_OPTIONS.find((option) => option.value === value)?.label ?? value
}

export interface OperatorOption {
  value: string
  label: string
  /** 判空类运算符只要左操作数。 */
  unary: boolean
  /** 比大小只对有序类型放行。 */
  ordering: boolean
}

export const OPERATOR_OPTIONS: OperatorOption[] = [
  { value: 'is', label: '等于', unary: false, ordering: false },
  { value: 'is not', label: '不等于', unary: false, ordering: false },
  { value: 'is null', label: '为空', unary: true, ordering: false },
  { value: 'is not null', label: '不为空', unary: true, ordering: false },
  { value: '<', label: '小于', unary: false, ordering: true },
  { value: '<=', label: '小于等于', unary: false, ordering: true },
  { value: '>', label: '大于', unary: false, ordering: true },
  { value: '>=', label: '大于等于', unary: false, ordering: true },
]

export function operatorOption(value: string): OperatorOption | null {
  return OPERATOR_OPTIONS.find((option) => option.value === value) ?? null
}

export function operatorLabel(value: string): string {
  return operatorOption(value)?.label ?? value
}

/** 某种数据类型能用的运算符：等值与判空全类型可用，比大小只给有序类型。 */
export function operatorsForDatatype(datatype: string | null): OperatorOption[] {
  if (datatype && ORDERED_DATATYPES.includes(datatype)) return OPERATOR_OPTIONS
  return OPERATOR_OPTIONS.filter((option) => !option.ordering)
}

/** 区块标题与说明。抽屉四块共用这份文案。 */
export const ACTION_SECTION_COPY = {
  basics: '基本信息',
  parameters: '参数',
  rules: '规则',
  criteria: '提交校验',
} as const

export const ACTION_COPY = {
  basicsHint: '动作名是给人和智能体看的语义；描述写清这个动作做成什么事。',
  parametersHint: '参数是执行动作的输入；参数保存后才能被规则与校验引用。',
  parametersEmpty: '还没有参数',
  rulesHint: '规则说明这个动作会改动什么；建对象 / 改对象可以给字段赋值。',
  rulesEmpty: '还没有规则',
  criteriaHint: '校验是执行动作的前置条件，全部成立才允许执行；不设就是不校验。',
  criteriaEmpty: '还没有校验条件',
  newRuleHint: '新建的规则还不能改种类：换种类等于换语义，删掉重建更清楚。',
  arrayHint: '多值参数暂时不能喂给单个字段，也不能当单个对象用。',
  referenceChanged: '引用的参数或本体元素已经不在了，请重新选择。',
  pendingDeletes: '将在确认改动后删除',
} as const

const STATIC_VALUE_PLACEHOLDER: Record<string, string> = {
  text: '文本',
  integer: '整数，如 3',
  decimal: '小数，如 1.5',
  boolean: 'true 或 false',
  date: '2026-01-31',
  timestamp: '2026-01-31T09:30',
  enum: '从候选值里选',
}

export function staticValuePlaceholder(datatype: string | null, isArray: boolean): string {
  const base = (datatype && STATIC_VALUE_PLACEHOLDER[datatype]) || '固定值'
  return isArray ? `${base}，多个用逗号分隔` : base
}

/** 图上的动作节点尺寸；画布与节点组件共用，别各写一份。 */
export const ACTION_NODE_WIDTH = 252
export const ACTION_NODE_HEIGHT = 62
