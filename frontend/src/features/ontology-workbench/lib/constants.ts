import type { IndexRun, RunStatus } from '../../../shared/types'

export const ONTOLOGY_PATH = '/ontology'
export const RESOURCE_QUERY_KEY = 'resource'
export const RESOURCE_REF_SEPARATOR = ':'

export const RESOURCE_SIDEBAR_WIDTH = 280
export const CONVERSATION_PANE_WIDTH = { default: 400, min: 320, max: 720 } as const
export const CONVERSATION_PANE_STORAGE_KEY = 'ontoweave.ontology.chat-pane-width'
export const RUN_POLL_INTERVAL_MS = 2_000
export const ACTIVE_RUN_STATUSES: RunStatus[] = ['QUEUED', 'RUNNING']
export const HIDDEN_ONTOLOGY_EVENT_PREFIXES = [
  'ontology.agent.turn',
  'ontology.agent.question',
  'ontology.state.changed',
]

export type ResourceCategory = 'object_type' | 'foreign_key' | 'many_to_many'
export type ResourceTabId = 'mapping' | 'overview' | 'data' | 'instances' | 'runs'

export interface ResourceTabDefinition {
  id: ResourceTabId
  label: string
}

export const RESOURCE_CATEGORY_LABELS: Record<ResourceCategory, string> = {
  object_type: '对象类型',
  foreign_key: '外键关系',
  many_to_many: '多对多关系',
}

export const RESOURCE_GROUPS: Array<{ category: ResourceCategory; label: string }> = [
  { category: 'object_type', label: '对象类型' },
  { category: 'foreign_key', label: '外键关系' },
  { category: 'many_to_many', label: '多对多关系' },
]

export const RESOURCE_TABS: Record<ResourceCategory, readonly ResourceTabDefinition[]> = {
  object_type: [
    { id: 'mapping', label: '对象映射' },
    { id: 'data', label: '对象数据' },
    { id: 'runs', label: '运行记录' },
  ],
  many_to_many: [
    { id: 'mapping', label: '关系映射' },
    { id: 'instances', label: '关系实例' },
    { id: 'runs', label: '运行记录' },
  ],
  foreign_key: [
    { id: 'overview', label: '关系概览' },
    { id: 'instances', label: '关系实例' },
    { id: 'runs', label: '运行记录' },
  ],
}

export const RESOURCE_COPY = {
  appLabel: '对象数据工作台',
  title: '对象数据',
  searchPlaceholder: '搜索对象或关系',
  emptyTitle: '还没有可映射的资源',
  emptyBody: '先在建模台确认对象类型，再回到这里接入数据集。',
  emptySearch: '没有匹配的对象或关系',
  loading: '正在读取对象与关系…',
  retry: '重新加载',
} as const

export const TAB_COPY = {
  objectMapping: {
    title: '对象映射画布',
    body: '这里将展示本体属性与数据集字段的连线、校验结果和生成入口。',
  },
  relationMapping: {
    title: '关系映射画布',
    body: '这里将展示连接表两端主键与目标关系类型的映射连线。',
  },
  objectData: {
    title: '对象数据',
    body: '这里将展示已生成的对象实例，并支持筛选、分页与详情查看。',
  },
  relationInstances: {
    title: '关系实例',
    body: '这里将分页展示已生成的关系实例，并可按两端主键过滤。',
  },
  relationOverview: {
    title: '关系概览',
    body: '这里将展示外键方向、两端对象状态和关系生成告警。',
  },
  runs: {
    title: '运行记录',
    body: '这里将展示全量生成的历史、阶段、计数和失败原因。',
  },
} as const

export const CONVERSATION_COPY = {
  eyebrow: 'Ontology workbench',
  title: '对象映射助手',
  emptyTitle: '把数据集接到本体上',
  emptyBody: '描述字段如何对应，或让 Agent 先给出映射建议与校验结果。',
  placeholder: '描述字段映射、数据范围或想检查的问题',
} as const

export const RESOURCE_STATUS_LABELS: Record<string, string> = {
  draft: '草案',
  confirmed: '已确认',
  approved: '已批准',
}

export const RUN_STATUS_LABELS: Record<RunStatus, string> = {
  QUEUED: '排队中',
  RUNNING: '生成中',
  SUCCEEDED: '已完成',
  FAILED: '失败',
  CANCELLED: '已取消',
  SUPERSEDED: '已被替代',
}

export function isActiveRun(run: IndexRun | null | undefined): run is IndexRun {
  return Boolean(run && ACTIVE_RUN_STATUSES.includes(run.status))
}
