import type { Conversation } from '../types'
import { request } from './client'

/** 新建业务会话；业务会话不绑定固定图，resource_id 只承担会话身份。 */
export function createBusinessConversation(): Promise<Conversation> {
  return request<Conversation>('/business/conversations', { method: 'POST' })
}
