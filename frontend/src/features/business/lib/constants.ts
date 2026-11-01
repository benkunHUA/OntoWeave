export const QUERY_PATH = '/business'
export const QUERY_NEW_PATH = '/business/new'

export const QUERY_DEFAULT_TITLE = '新业务会话'
export const QUERY_TITLE_MAX_LENGTH = 24

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
  deleteTitle: '删除业务会话',
  deleteBody: '删除后会话与消息无法恢复。',
  deleteConfirm: '删除',
  deleting: '删除中…',
  cancel: '取消',
  evidenceTitle: '查询证据',
  evidenceEmpty: '本次回答没有返回结构化证据。',
  evidenceIndexVersion: '索引版本',
  evidencePrimaryKey: '主键',
  evidenceObjectFallback: '业务对象',
  evidenceFieldFallback: '字段',
  evidenceNoProperties: '暂无属性',
  trueValue: '是',
  falseValue: '否',
} as const
