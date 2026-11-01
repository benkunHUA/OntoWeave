import { describe, expect, it } from 'vitest'
import { QUERY_TITLE_MAX_LENGTH } from './constants'
import { deriveQueryTitle } from './title'

describe('deriveQueryTitle', () => {
  it('normalizes whitespace', () => {
    expect(deriveQueryTitle('  查询   订单  ')).toBe('查询 订单')
  })

  it('truncates long questions', () => {
    const title = deriveQueryTitle('订'.repeat(QUERY_TITLE_MAX_LENGTH + 5))
    expect(title).toHaveLength(QUERY_TITLE_MAX_LENGTH + 1)
    expect(title.endsWith('…')).toBe(true)
  })
})
