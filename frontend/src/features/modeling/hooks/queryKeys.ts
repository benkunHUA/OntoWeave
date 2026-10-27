/** 查询键集中在这里：失效与取数的键必须字面一致，散落各处迟早对不上。 */

export const conversationKeys = {
  all: ['conversations'] as const,
}

export const workspaceKeys = {
  detail: (conversationId: string) => ['workspace', conversationId] as const,
}

export const publicationKeys = {
  detail: (graphId: string) => ['graph-publication', graphId] as const,
}

export const actionKeys = {
  /** 动作详情：改动集一翻篇（保存 / 确认 / 撤销）定义就会变，整族一起失效。 */
  all: ['actions'] as const,
  detail: (actionId: string) => ['actions', actionId] as const,
}
