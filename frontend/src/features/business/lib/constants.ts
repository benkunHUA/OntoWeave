export const QUERY_PATH = '/business'
export const QUERY_NEW_PATH = '/business/new'

export const QUERY_DEFAULT_TITLE = '新业务会话'
// 与后端 ConversationUpdate.title 的 120 字上限一致；列表宽度不够时由 CSS 省略。
export const QUERY_TITLE_MAX_LENGTH = 120

export const QUERY_COPY = {
  brandTitle: '业务工作台',
  brandSubtitle: 'OntoWeave',
  newConversation: '新建业务会话',
  creating: '创建中…',
  searchPlaceholder: '搜索业务会话',
  listEmpty: '还没有业务会话',
  listEmptyHint: '新建一个会话，用业务语言查询已发布业务图，或执行图内行动。',
  selectEmptyTitle: '选择或新建一个业务会话',
  selectEmptyBody: '左侧保存你的业务会话；查询会附带证据，行动会在写入前展示确认卡片。',
  chatEmptyTitle: '从一个业务问题或行动开始',
  chatEmptyBody: '例如：关闭工单 T-1001，并把解决说明记为「已更换硒鼓」。',
  composerPlaceholder: '输入业务问题或行动，例如「关闭工单 T-1001」',
  loading: '正在读取业务会话…',
  executionTraceTitle: '查询过程',
  deleteTitle: '删除业务会话',
  deleteBody: '删除后会话与消息无法恢复。',
  deleteConfirm: '删除',
  deleting: '删除中…',
  cancel: '取消',
  evidenceGraphTitle: '对象实例图',
  evidenceGraphLabel: '查询证据关系图',
  evidenceGraphObjects: '个对象',
  evidenceGraphRelations: '条关系',
  evidenceGraphNoEdges: '本次查询没有形成关系连线',
  evidenceGraphRelationUnavailable: '关系索引未建立，部分连线不可用',
  evidenceGraphTruncated: '部分查询结果被截断',
  evidenceGraphRelationMissing: '业务图关系定义未完整返回',
  trueValue: '是',
  falseValue: '否',
} as const

export const EVIDENCE_GRAPH_LAYOUT = {
  nodeWidth: 156,
  nodeHeight: 48,
  margin: 16,
  labelCharWidth: 6.2,
  labelHeight: 15,
  minWidth: 280,
  minHeight: 120,
} as const

export const EVIDENCE_WARNING_COPY: Record<string, string> = {
  truncated: QUERY_COPY.evidenceGraphTruncated,
  relation_not_indexed: QUERY_COPY.evidenceGraphRelationUnavailable,
  relation_schema_missing: QUERY_COPY.evidenceGraphRelationMissing,
}
