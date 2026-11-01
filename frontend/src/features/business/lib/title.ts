import { QUERY_TITLE_MAX_LENGTH } from './constants'

/** 会话还没有标题时，用第一句问题生成一个可读标题。 */
export function deriveQueryTitle(text: string): string {
  const normalized = text.replace(/\s+/g, ' ').trim()
  if (!normalized) return '新问答'
  return normalized.length > QUERY_TITLE_MAX_LENGTH
    ? `${normalized.slice(0, QUERY_TITLE_MAX_LENGTH)}…`
    : normalized
}
