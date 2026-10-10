/** 工具名到中文动作的映射；后端只给机器名，展示文案由前端维护。 */
export const TOOL_LABELS: Record<string, string> = {
  search_ontology: '检索已有本体',
  list_types: '列出现有类型',
  get_subgraph: '读取相关子图',
  propose_type: '提出对象类型',
  propose_property: '补充属性',
  propose_relation: '建立关系',
  update_element: '修改草稿',
  remove_element: '标记删除',
  ask_user: '等待你的判断',
  task: '委派视觉整理',
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

export function toolLabel(name: string): string {
  return TOOL_LABELS[name] ?? `正在执行 ${name}`
}
