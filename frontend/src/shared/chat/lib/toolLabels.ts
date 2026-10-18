/** 工具名到中文动作的映射；后端只给机器名，展示文案由前端维护。 */
export const TOOL_LABELS: Record<string, string> = {
  search: '检索已有元素',
  get_subgraph: '读取相关子图',
  propose_type: '提出对象类型',
  reuse_type: '复用已有类型',
  propose_property: '补充属性',
  propose_relation: '建立关系',
  update_element: '修改草稿',
  remove_element: '标记删除',
  ask_user: '征询你的判断',
  search_actions: '查已有动作',
  get_action: '读取动作定义',
  get_object_type: '读取对象类型',
  propose_action: '新建动作',
  reuse_action: '复用已有动作',
  propose_action_parameter: '添加动作参数',
  propose_action_rule: '添加动作规则',
  set_action_criteria: '设置提交校验',
  get_view_state: '读取视图设置',
  get_ontology_slice: '读取画布结构',
  set_layout: '调整布局',
  focus: '移动视野',
  fit_view: '适应画面',
  reset_view: '重置视图',
  highlight: '高亮节点',
  dim: '弱化节点',
  group: '整理分组',
  collapse: '折叠分组',
  expand: '展开分组',
  filter_view: '过滤显示',
  annotate: '添加标注',
  save_view: '保存视图',
  load_view: '读取视图',
}

/** 子智能体名到中文称谓：委派行与它下面的内部步骤共用一套叫法。 */
export const SUBAGENT_LABELS: Record<string, string> = {
  action_modeler: '动作建模',
  view_designer: '视觉整理',
}

export function subagentLabel(name: string): string {
  return SUBAGENT_LABELS[name] ?? name
}

/** 工具调用的一行文案：委派要说清派给了哪个子智能体。 */
export function toolLabel(name: string, args: Record<string, unknown> = {}): string {
  if (name === 'task') {
    const target = typeof args.subagent_type === 'string' ? args.subagent_type : ''
    return target ? `委派 · ${subagentLabel(target)}` : '委派子智能体'
  }
  return TOOL_LABELS[name] ?? `正在执行 ${name}`
}
