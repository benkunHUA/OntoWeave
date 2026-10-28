export const QUERY_PATH = '/query'
export const QUERY_NEW_PATH = '/query/new'

export const QUERY_DEFAULT_TITLE = '新问答'
export const QUERY_TITLE_MAX_LENGTH = 24

export const QUERY_COPY = {
  brandTitle: '业务问答',
  brandSubtitle: 'OntoWeave',
  newConversation: '新建问答',
  creating: '创建中…',
  searchPlaceholder: '搜索问答会话',
  listEmpty: '还没有问答会话',
  listEmptyHint: '新建一个会话，用业务语言直接查询已发布业务图。',
  selectEmptyTitle: '选择或新建一个问答',
  selectEmptyBody: '左侧保存你的查询会话；每个问题都会从选图开始，回答会附带查询证据。',
  chatEmptyTitle: '从一个业务问题开始',
  chatEmptyBody: '例如：华东区最近 30 天金额最高的订单由哪个客户下的？',
  composerPlaceholder: '输入业务问题，例如「统计上个月退货率最高的商品」',
  loading: '正在读取问答记录…',
  deleteTitle: '删除问答',
  deleteBody: '删除后会话与消息无法恢复。',
  deleteConfirm: '删除',
  deleting: '删除中…',
  cancel: '取消',
  evidenceTitle: '查询证据',
  evidenceEmpty: '本次回答没有返回结构化证据。',
  evidenceStep: '步骤',
  evidenceIndexVersion: '索引版本',
  evidencePrimaryKey: '主键',
  evidenceObjectFallback: '业务对象',
  evidenceFieldFallback: '字段',
  evidenceNoProperties: '暂无属性',
  trueValue: '是',
  falseValue: '否',
} as const
