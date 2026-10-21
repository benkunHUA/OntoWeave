import { describe, expect, it } from 'vitest'
import type { Pipeline } from '../../../shared/types'
import {
  scheduleRunLabel,
  scheduleRunStatusLabel,
  scheduleVersionHint,
} from './schedule'

describe('schedule labels', () => {
  it('formats skipped schedule reason', () => {
    expect(scheduleRunLabel({ status: 'ignored', reason_code: 'NO_CHANGE' }))
      .toBe('输入和逻辑未变化，已跳过')
    expect(scheduleRunLabel({ status: 'ignored', reason_code: 'NO_ACTIVE_VERSION' }))
      .toBe('尚未成功构建，跳过')
    expect(scheduleRunLabel({ status: 'dispatched', reason_code: null }))
      .toBe('已创建构建')
  })

  it('labels run status and warns when the draft is ahead of the published version', () => {
    expect(scheduleRunStatusLabel('pending')).toBe('等待构建空位')
    const pipeline = {
      id: 'pipeline-1',
      conversation_id: 'conversation-1',
      name: 'Orders',
      status: 'active',
      draft_revision: 9,
      current_version_id: 'version-1',
      created_at: null,
      updated_at: null,
    } satisfies Pipeline

    expect(scheduleVersionHint(pipeline, true)).toBe('调度仍使用已发布版本')
  })

  it('requires a first build before scheduling', () => {
    const pipeline = {
      id: 'pipeline-1',
      conversation_id: 'conversation-1',
      name: 'Orders',
      status: 'draft',
      draft_revision: 2,
      current_version_id: null,
      created_at: null,
      updated_at: null,
    } satisfies Pipeline

    expect(scheduleVersionHint(pipeline, true)).toBe('首次构建后才能定时运行')
  })
})
