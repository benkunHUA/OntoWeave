import type { Status } from '../../../shared/types'

/** 本体元素状态的中文文案，抽屉与画布共用。 */
export const STATUS_LABEL: Record<Status, string> = {
  draft: '草稿',
  confirmed: '已确认',
  approved: '已批准',
}
