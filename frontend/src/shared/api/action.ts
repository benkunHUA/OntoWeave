import type { ActionDetail } from '../types'
import { request } from './client'

/** 动作详情：参数 / 规则 + 字段赋值 / 提交校验整份读回来，抽屉按它建草稿。 */
export function getActionDetail(actionId: string): Promise<ActionDetail> {
  return request<ActionDetail>(`/actions/${actionId}`)
}
