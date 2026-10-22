export const DATA_PIPELINE_PATH = '/pipelines'

export const PIPELINE_LIST_WIDTH = 268
export const PIPELINE_CHAT_WIDTH = {
  default: 400,
  min: 320,
  max: 640,
} as const
export const PIPELINE_CHAT_STORAGE_KEY = 'ontoweave.data-pipeline.chat-pane-width'

export const NODE_SIZE = {
  default: { width: 224, height: 82 },
  source: { width: 224, height: 82 },
  transform: { width: 148, height: 148 },
  output: { width: 224, height: 82 },
} as const
export const NODE_GAP_X = 96
export const NODE_GAP_Y = 48
export const CONFIG_SAVE_DELAY_MS = 400
export const BUILD_POLL_INTERVAL_MS = 2_000
export const BUILD_STORAGE_PREFIX = 'ontoweave.data-pipeline.build.'

export const NODE_DRAG_MIME = 'application/ontoweave-pipeline-node'

export const PIPELINE_DEFAULT_NAME = '新数据整理流程'

export const PIPELINE_NEW_COPY = {
  create: '新建数据整理流程',
  creating: '创建中',
} as const

export const PIPELINE_RENAME_COPY = {
  title: '重命名数据整理流程',
  subtitle: '名称会同步到流程列表和画布标题',
  label: '流程名称',
  cancel: '取消',
  save: '保存',
  saving: '保存中',
} as const

export const PIPELINE_DELETE_COPY = {
  action: '删除流程',
  title: '删除数据整理流程',
  subtitle: '删除后流程将不再出现在工作台',
  body: '删除后流程及其对话会从工作台移除，且无法恢复。',
  cancel: '取消',
  confirm: '删除',
  deleting: '删除中…',
} as const

export const CATEGORY_LABELS: Record<string, string> = {
  source: '输入',
  transform: '转换',
  output: '输出',
}

export const CATEGORY_ORDER = ['source', 'transform', 'output'] as const

export const PIPELINE_STATUS_LABELS: Record<string, string> = {
  draft: '草稿',
  active: '已发布',
  archived: '已归档',
}

export const PIPELINE_EMPTY_COPY = {
  list: '还没有数据整理流程',
  listHint: '新建流程后，从对话或画布开始搭建。',
  conversationTitle: '数据整理助手',
  conversationEmptyTitle: '从一条数据流程开始',
  conversationEmptyBody: '拖动节点，或描述你希望如何清洗数据。Agent 会同步修改画布。',
  canvasEmpty: '从左侧拖入一个节点',
}

export const NODE_CONFIG_COPY = {
  title: '节点配置',
  name: '节点名称',
  type: '节点类型',
  inputs: '输入字段',
  outputs: '输出字段',
  issues: '检查问题',
  noIssues: '当前节点没有检查问题',
  preview: '预览',
  delete: '删除节点',
  deleteConfirm: '确认删除这个节点及其连线？',
  cancel: '取消',
  saving: '保存中',
  saved: '已保存',
  invalidName: '节点名称不能为空',
  unknownType: '节点目录中没有这个类型',
}

export const FIELD_LABELS: Record<string, string> = {
  datasetKind: '数据集类型',
  datasetId: '输入数据集',
  condition: '筛选条件',
  mode: '模式',
  columns: '字段列表',
  renames: '重命名规则',
  outputField: '输出字段',
  expression: '表达式',
  joinType: '连接类型',
  keys: '连接键',
  groupBy: '分组字段',
  aggregations: '聚合项',
  outputId: '输出标识',
}

export const ISSUE_COPY = {
  blocking: '阻塞',
  warning: '提醒',
  empty: '当前节点没有检查问题',
}

export const CONFLICT_COPY = {
  title: '草稿已被其他会话修改',
  body: '为避免覆盖最新改动，写入队列已停止。重新加载后可继续编辑。',
  reload: '重新加载草稿',
}

export const CANVAS_COPY = {
  fit: '整理布局',
  conflict: '草稿冲突',
  revision: '修订',
}

export const WIDGETS = {
  text: 'text',
  number: 'number',
  boolean: 'boolean',
  select: 'select',
  multiSelect: 'multi-select',
  fieldSelector: 'field-selector',
  operatorSelector: 'operator-selector',
  conditionTree: 'condition-tree',
  renameList: 'rename-list',
  joinKeys: 'join-keys',
  aggregationList: 'aggregation-list',
  expressionTree: 'expression-tree',
} as const
