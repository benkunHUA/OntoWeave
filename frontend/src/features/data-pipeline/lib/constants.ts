export const DATA_PIPELINE_PATH = '/pipelines'

export const PIPELINE_LIST_WIDTH = 280
export const PIPELINE_CANVAS_WIDTH = {
  default: 640,
  min: 480,
  max: 920,
} as const
export const PIPELINE_CANVAS_STORAGE_KEY = 'ontoweave.data-pipeline.canvas-pane-width'
export const PIPELINE_CANVAS_VAR = '--pipeline-canvas-w'

export const NODE_WIDTH = 224
export const NODE_HEIGHT = 82
export const NODE_GAP_X = 96
export const NODE_GAP_Y = 48
export const CONFIG_SAVE_DELAY_MS = 400
export const BUILD_POLL_INTERVAL_MS = 2_000
export const BUILD_STORAGE_PREFIX = 'ontoweave.data-pipeline.build.'

export const NODE_DRAG_MIME = 'application/ontoweave-pipeline-node'

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
