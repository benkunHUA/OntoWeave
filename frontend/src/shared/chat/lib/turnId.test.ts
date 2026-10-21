import { describe, expect, it, vi } from 'vitest'
import { resolveTurnId } from './turnId'

describe('resolveTurnId', () => {
  it('generates a new id for a new user message', () => {
    const generated = '00000000-0000-4000-8000-000000000001'
    vi.spyOn(crypto, 'randomUUID').mockReturnValue(generated)
    expect(resolveTurnId('old-turn', false)).toBe(generated)
  })

  it('reuses the interrupted turn when resuming', () => {
    expect(resolveTurnId('old-turn', true)).toBe('old-turn')
  })
})
