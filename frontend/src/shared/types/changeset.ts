/**
 * 能直接编辑的元素类型：本体元素 + 动作层的全部子元素。
 * 与后端 `services/element_kinds.REGISTRY` 的键一一对应。
 */
export type ElementKind =
  | 'object_type'
  | 'property'
  | 'relation'
  | 'action_type'
  | 'action_parameter'
  | 'action_rule'
  | 'action_rule_assignment'
  | 'action_criteria_node'
  | 'action_criteria_operand'

/** 改动集 op 的 payload 里 `kind` 的取值：本体元素，或视图改动。 */
export type ChangesetOpKind = ElementKind | 'view'

/** patch 里允许的值：文本、数字、布尔、清空，或一串候选值（枚举）。 */
export type EditPatchValue = string | number | boolean | string[] | null

/** 改已有元素：只带真正变了的字段。 */
export interface ElementUpdateEdit {
  op: 'update'
  element_kind: ElementKind
  element_id: string
  patch: Record<string, EditPatchValue>
}

/** 给对象类型新增一个字段。 */
export interface PropertyCreateEdit {
  op: 'create_property'
  owner_type_id: string
  name: string
  name_en?: string | null
  datatype: string
  is_array: boolean
  is_primary_key: boolean
  is_title_key: boolean
  /** datatype = enum 时的候选值；其余类型不发这个字段。 */
  enum_values?: string[] | null
}

/** 标记删除一个元素（抽屉里只删字段）。 */
export interface ElementDeleteEdit {
  op: 'delete'
  element_kind: ElementKind
  element_id: string
}

/** 新建一个草稿动作类型，并挂进当前图。 */
export interface ActionTypeCreateEdit {
  op: 'create_action_type'
  name: string
  description?: string | null
}

/** 给动作类型加一个草稿参数；`kind` 决定 datatype 与 target_object_type_id 哪边有值。 */
export interface ActionParameterCreateEdit {
  op: 'add_action_parameter'
  action_type_id: string
  name: string
  kind?: string
  description?: string | null
  datatype?: string | null
  target_object_type_id?: string | null
  is_array?: boolean
  is_required?: boolean
  default_value?: unknown
  enum_values?: string[] | null
}

/** 字段赋值的入参：值来源决定哪几列有值。 */
export interface ActionAssignmentIn {
  target_property_id: string
  value_source: string
  value_parameter_id?: string | null
  object_parameter_id?: string | null
  object_property_id?: string | null
  static_value?: unknown
}

/** 给动作类型加一条草稿规则；字段赋值内联，省得再补一轮。 */
export interface ActionRuleCreateEdit {
  op: 'add_action_rule'
  action_type_id: string
  rule_kind: string
  target_object_type_id?: string | null
  target_relation_id?: string | null
  source_parameter_id?: string | null
  target_parameter_id?: string | null
  assignments?: ActionAssignmentIn[]
}

/** 给已有规则补一条字段赋值；新规则走 `add_action_rule` 内联赋值。 */
export interface ActionRuleAssignmentCreateEdit extends ActionAssignmentIn {
  op: 'add_action_rule_assignment'
  rule_id: string
}

/** 条件的一侧操作数。 */
export interface ActionOperandIn {
  kind: string
  value_parameter_id?: string | null
  object_parameter_id?: string | null
  object_property_id?: string | null
  static_value?: unknown
}

/** 一条提交校验条件；判空类运算符没有右操作数。 */
export interface ActionConditionIn {
  operator: string
  failure_message?: string | null
  left: ActionOperandIn
  right?: ActionOperandIn | null
}

/** 整棵替换提交校验：交一份完整条件列表，空列表就是「不设校验」。 */
export interface ActionCriteriaSetEdit {
  op: 'set_action_criteria'
  action_type_id: string
  conditions: ActionConditionIn[]
}

/** 新建一个草稿对象类型，并挂进当前图。 */
export interface ObjectTypeCreateEdit {
  op: 'create_object_type'
  name: string
  name_en?: string | null
  description?: string | null
}

/** 把共享池里已确认的对象类型并进当前图。 */
export interface ObjectTypeReuseEdit {
  op: 'reuse_object_type'
  object_type_id: string
}

/** 新建一条草稿关系；两端类型不存在时会一并挂进当前图。 */
export interface RelationCreateEdit {
  op: 'create_relation'
  source_type_id: string
  target_type_id: string
  name: string
  direction?: string
  cardinality?: string
  forward_label?: string | null
  reverse_label?: string | null
  foreign_key_property_id?: string | null
}

/** 抽屉一次保存里的全部编辑，整批落进同一个改动集。 */
export type GraphEditOp =
  | ElementUpdateEdit
  | PropertyCreateEdit
  | ElementDeleteEdit
  | ObjectTypeCreateEdit
  | ObjectTypeReuseEdit
  | RelationCreateEdit
  | ActionTypeCreateEdit
  | ActionParameterCreateEdit
  | ActionRuleCreateEdit
  | ActionRuleAssignmentCreateEdit
  | ActionCriteriaSetEdit

export interface ChangesetOp {
  id: string
  /** view 是绘图子智能体对视图状态的改动：即时生效，撤销时一并回滚。 */
  op: 'create' | 'update' | 'delete' | 'view'
  target_id: string | null
  payload: {
    kind?: ChangesetOpKind
    before?: Record<string, unknown>
    after?: Record<string, unknown>
    [key: string]: unknown
  }
}

export interface Changeset {
  id: string
  graph_id: string
  status: 'open' | 'confirmed' | 'rejected'
  ops: ChangesetOp[]
}
