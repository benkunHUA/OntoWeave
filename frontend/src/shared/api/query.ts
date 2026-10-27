import type { Conversation } from '../types'
import { request } from './client'

/** 新建业务问答会话；查询会话不绑定固定图，resource_id 只承担会话身份。 */
export function createQueryConversation(): Promise<Conversation> {
  return request<Conversation>('/query/conversations', { method: 'POST' })
}
