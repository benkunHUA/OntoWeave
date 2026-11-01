import type { ChatMessage } from '../../../shared/types'
import { textFromBlocks } from '../../../shared/chat/lib/messageBlocks'
import { QUERY_TITLE_MAX_LENGTH } from './constants'

function normalizeTitle(text: string): string {
  return text.replace(/\s+/g, ' ').trim()
}

/** 会话还没有标题时，用第一句问题生成一个可读标题。 */
export function deriveQueryTitle(text: string): string {
  const normalized = normalizeTitle(text)
  if (!normalized) return '新问答'
  return normalized.length > QUERY_TITLE_MAX_LENGTH
    ? `${normalized.slice(0, QUERY_TITLE_MAX_LENGTH)}…`
    : normalized
}

/**
 * 标题栏优先展示完整问题。旧数据在创建时被截成 24 字并写入了省略号，
 * 这里用首条用户消息把标题补全；手动改过名的标题不会被覆盖。
 */
export function queryHeaderTitle(title: string, messages: ChatMessage[]): string {
  if (!title.endsWith('…')) return title
  const question = messages.find((message) => message.role === 'user')
  if (!question) return title
  const full = normalizeTitle(textFromBlocks(question.blocks))
  if (!full) return title
  return full.startsWith(title.slice(0, -1)) ? full : title
}
