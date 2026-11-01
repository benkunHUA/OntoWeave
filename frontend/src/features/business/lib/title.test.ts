import { describe, expect, it } from 'vitest'
import { QUERY_TITLE_MAX_LENGTH } from './constants'
import type { ChatMessage } from '../../../shared/types'
import { deriveQueryTitle, queryHeaderTitle } from './title'

describe('deriveQueryTitle', () => {
  it('normalizes whitespace', () => {
    expect(deriveQueryTitle('  查询   订单  ')).toBe('查询 订单')
  })

  it('keeps a reasonably long question intact', () => {
    const question = '苏泽宁最新的订单买的商品是什么？商品的供应商是什么'
    expect(deriveQueryTitle(question)).toBe(question)
  })

  it('truncates long questions', () => {
    const title = deriveQueryTitle('订'.repeat(QUERY_TITLE_MAX_LENGTH + 5))
    expect(title).toHaveLength(QUERY_TITLE_MAX_LENGTH + 1)
    expect(title.endsWith('…')).toBe(true)
  })
})

describe('queryHeaderTitle', () => {
  const question = '苏泽宁最新的订单买的商品是什么？商品的供应商是什么'
  const messages: ChatMessage[] = [
    {
      id: 'message-1',
      role: 'user',
      blocks: [{ id: 'block-1', kind: 'text', text: question, done: true }],
    },
  ]

  it('restores the full question for titles truncated by old data', () => {
    expect(queryHeaderTitle(`${question.slice(0, 24)}…`, messages)).toBe(question)
  })

  it('does not overwrite a manually renamed title', () => {
    expect(queryHeaderTitle('供应商分析', messages)).toBe('供应商分析')
  })
})
