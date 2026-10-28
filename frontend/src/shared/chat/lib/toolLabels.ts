/** 工具名到中文动作的映射；后端只给机器名，展示文案由前端维护。 */
export const TOOL_LABELS: Record<string, string> = {
  // 建模
  search: '检索已有元素',
  get_subgraph: '读取相关子图',
  propose_type: '提出对象类型',
  reuse_type: '复用已有类型',
  propose_property: '补充属性',
  propose_relation: '建立关系',
  update_element: '修改草稿',
  remove_element: '标记删除',
  ask_user: '征询你的判断',

  // 动作
  search_actions: '查已有动作',
  get_action: '读取动作定义',
  get_object_type: '读取对象类型',
  propose_action: '新建动作',
  reuse_action: '复用已有动作',
  propose_action_parameter: '添加动作参数',
  propose_action_rule: '添加动作规则',
  set_action_criteria: '设置提交校验',

  // 视图
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

  // 业务查询
  rewrite_query: '改写查询',
  search_graphs: '检索业务图',
  get_graph_detail: '读取业务图详情',
  query_objects: '查询对象',
  get_related_objects: '沿关系取对象',

  // 数据连接
  get_data_connection_state: '读取连接状态',
  get_connection_events: '读取连接记录',
  list_source_tables: '查看源表',
  get_source_table_schema: '读取源表结构',

  // 数据整理
  get_pipeline_state: '读取流程状态',
  get_pipeline_draft: '读取流程草稿',
  get_pipeline_check_result: '读取流程校验结果',
  get_pipeline_events: '读取流程记录',
  get_node_catalog: '读取节点目录',
  list_pipeline_input_datasets: '查看流程输入数据集',
  get_dataset_schema: '读取数据集结构',
  get_output_sources: '读取输出来源',
  add_pipeline_node: '添加流程节点',
  update_pipeline_node: '修改流程节点',
  delete_pipeline_node: '删除流程节点',
  connect_pipeline_nodes: '连接流程节点',
  disconnect_pipeline_nodes: '断开流程节点',
  set_pipeline_output: '设置流程输出',
  preview_pipeline_node: '预览流程节点',
  build_pipeline: '构建数据流程',

  // 映射与索引
  find_bindable_datasets: '查找可绑定数据集',
  get_subject_definition: '读取主体定义',
  get_mapping: '读取字段映射',
  suggest_mapping: '建议字段映射',
  validate_mapping: '校验字段映射',
  preview_mapping: '预览字段映射',
  update_mapping: '修改字段映射',
  start_generation: '开始生成数据',
  rebind_dataset: '重新绑定数据集',
  unbind: '解除绑定',
  list_index_runs: '查看索引任务',
  get_index_run: '读取索引任务',
  cancel_index_run: '取消索引任务',

  // deepagents 内置能力
  write_todos: '更新任务计划',
  read_file: '读取文件',
  write_file: '写入文件',
  edit_file: '编辑文件',
  ls: '查看目录',
  glob: '查找文件',
  grep: '搜索文件',
  delete: '删除文件',
  execute: '执行命令',
}

/** 子智能体名到中文称谓：委派行与它下面的内部步骤共用一套叫法。 */
export const SUBAGENT_LABELS: Record<string, string> = {
  action_modeler: '动作建模',
  view_designer: '视觉整理',
}

export function subagentLabel(name: string): string {
  return SUBAGENT_LABELS[name] ?? '子智能体'
}

/** 工具调用的一行文案：委派要说清派给了哪个子智能体。 */
export function toolLabel(name: string, args: Record<string, unknown> = {}): string {
  if (name === 'task') {
    const target = typeof args.subagent_type === 'string' ? args.subagent_type : ''
    return target ? `委派 · ${subagentLabel(target)}` : '委派子智能体'
  }
  return TOOL_LABELS[name] ?? '执行工具'
}
