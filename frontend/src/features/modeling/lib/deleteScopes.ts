import type { DeleteScope } from '../../../shared/types'

/** 删除会话时默认选中的范围：只删图，共享池原样保留。 */
export const DEFAULT_DELETE_SCOPE: DeleteScope = 'graph-only'

export interface DeleteScopeOption {
  value: DeleteScope
  /** 单选按钮的主标题。 */
  title: string
  /** 主标题下面那行说明，讲清楚这个范围会动到什么。 */
  hint: string
}

export const DELETE_SCOPE_OPTIONS: DeleteScopeOption[] = [
  {
    value: 'graph-only',
    title: '仅删除图',
    hint: '草稿仍留在共享池里，之后还能复用。',
  },
  {
    value: 'graph-and-drafts',
    title: '删除图和里面的草稿对象和关系',
    hint: '只清本图新建、尚未确认的元素；已确认的会保留。',
  },
  {
    value: 'graph-and-unreferenced',
    title: '删除图和图里面未被引用的对象和关系',
    hint: '本图独有、没被别的图引用的元素一律清掉；被共用的会保留。',
  },
]
