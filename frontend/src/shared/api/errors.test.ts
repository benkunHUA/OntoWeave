import { describe, expect, it } from 'vitest'
import { HttpError } from './client'
import { httpErrorInfo, httpErrorText } from './errors'

describe('http error presentation', () => {
  it('extracts message, code, details, and blocking issues from JSON errors', () => {
    const error = new HttpError(422, JSON.stringify({
      code: 'PIPELINE_NOT_READY',
      message: '流程未就绪',
      details: { blockingIssues: [{ code: 'NEEDS_OUTPUT' }] },
    }))

    const info = httpErrorInfo(error, '请求失败')

    expect(info.message).toBe('流程未就绪')
    expect(info.code).toBe('PIPELINE_NOT_READY')
    expect(info.details).toEqual({ blockingIssues: [{ code: 'NEEDS_OUTPUT' }] })
    expect(info.issues).toHaveLength(1)
  })

  it('does not expose a raw JSON body as the user-facing message', () => {
    expect(httpErrorText(new Error('{"code":"X"}'), '加载失败')).toBe('加载失败')
    expect(httpErrorText(new HttpError(409, '{"message":"流程已归档"}'), '加载失败'))
      .toBe('流程已归档')
  })
})
