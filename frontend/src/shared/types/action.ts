import type { Status } from './common'

/** 动作碰到的本体元素；画布据此在动作节点与它之间连一条 `involves` 边。 */
export interface ActionInvolves {
  kind: 'object_type' | 'relation'
  id: string
}

/** 画布上的动作节点要的信息；全量定义走动作详情接口。 */
export interface ActionBrief {
  id: string
  name: string
  description: string | null
  status: Status
  involves: ActionInvolves[]
}

/** 一个动作参数；`kind` 决定 datatype 与 target_object_type_id 哪边有值。 */
export interface ActionParameterDetail {
  id: string
  name: string
  description: string | null
  kind: string
  datatype: string | null
  target_object_type_id: string | null
  is_array: boolean
  is_required: boolean
  default_value: unknown
  enum_values: string[] | null
  order_index: number
  status: Status
}

/** 规则里的一条字段赋值：目标属性 ← 一个值来源。 */
export interface ActionAssignmentDetail {
  id: string
  target_property_id: string
  value_source: string
  value_parameter_id: string | null
  object_parameter_id: string | null
  object_property_id: string | null
  static_value: unknown
  status: Status
}

/** 一条规则与它的字段赋值；赋值是规则的子元素，读的时候跟着一起来。 */
export interface ActionRuleDetail {
  id: string
  rule_kind: string
  target_object_type_id: string | null
  target_relation_id: string | null
  source_parameter_id: string | null
  target_parameter_id: string | null
  order_index: number
  status: Status
  assignments: ActionAssignmentDetail[]
}

/** 条件的一侧操作数；`kind` 决定哪几列有值。 */
export interface ActionOperandDetail {
  side: string
  kind: string
  value_parameter_id: string | null
  object_parameter_id: string | null
  object_property_id: string | null
  static_value: unknown
}

/** 提交校验树的一个节点。v1 只长一层：一个 `and` 根 + 扁平条件。 */
export interface ActionCriteriaNodeDetail {
  id: string
  node_kind: string
  logic: string | null
  operator: string | null
  failure_message: string | null
  order_index: number
  left: ActionOperandDetail | null
  right: ActionOperandDetail | null
  children: ActionCriteriaNodeDetail[]
}

export interface ActionDetail {
  id: string
  name: string
  description: string | null
  status: Status
  parameters: ActionParameterDetail[]
  rules: ActionRuleDetail[]
  criteria: ActionCriteriaNodeDetail[]
}
