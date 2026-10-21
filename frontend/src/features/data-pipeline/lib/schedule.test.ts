import { describe, expect, it } from 'vitest'
import type { Pipeline } from '../../../shared/types'
import {
  scheduleRunLabel,
  scheduleRunStatusLabel,
  scheduleVersionHint,
  triggerTypeLabel,
} from './schedule'
import { formatDateTime } from './format'

describe('schedule labels', () => {
  it('formats skipped schedule reason', () => {
    expect(scheduleRunLabel({ status: 'ignored', reason_code: 'NO_CHANGE' }))
      .toBe('输入和逻辑未变化，已跳过')
    expect(scheduleRunLabel({ status: 'ignored', reason_code: 'NO_ACTIVE_VERSION' }))
      .toBe('尚未成功构建，跳过')
    expect(scheduleRunLabel({ status: 'dispatched', reason_code: null }))
      .toBe('已创建构建')
  })

  it('labels run status and names the published version when the draft is ahead', () => {
    expect(scheduleRunStatusLabel('pending')).toBe('等待构建空位')
    const pipeline = {
      id: 'pipeline-1',
      conversation_id: 'conversation-1',
      name: 'Orders',
      status: 'active',
      draft_revision: 9,
      current_version_id: 'version-1',
      current_version_number: 2,
      current_version_draft_revision: 7,
      created_at: null,
      updated_at: null,
    } satisfies Pipeline

    expect(scheduleVersionHint(pipeline, 9)).toBe('调度仍使用已发布版本 v2')
  })

  it('requires a first build before scheduling', () => {
    const pipeline = {
      id: 'pipeline-1',
      conversation_id: 'conversation-1',
      name: 'Orders',
      status: 'draft',
      draft_revision: 2,
      current_version_id: null,
      current_version_number: null,
      current_version_draft_revision: null,
      created_at: null,
      updated_at: null,
    } satisfies Pipeline

    expect(scheduleVersionHint(pipeline, 2)).toBe('首次构建后才能定时运行')
  })

  it('does not warn when the published version was built from the current draft', () => {
    const pipeline = {
      id: 'pipeline-1',
      conversation_id: 'conversation-1',
      name: 'Orders',
      status: 'active',
      draft_revision: 9,
      current_version_id: 'version-1',
      current_version_number: 3,
      current_version_draft_revision: 9,
      created_at: null,
      updated_at: null,
    } satisfies Pipeline

    expect(scheduleVersionHint(pipeline, 9)).toBeNull()
  })

  it('labels time triggers and formats schedule timestamps in the schedule timezone', () => {
    expect(triggerTypeLabel('time')).toBe('时间触发')
    expect(triggerTypeLabel('manual')).toBe('手动触发')
    expect(formatDateTime('2026-10-21T01:00:00Z', 'Asia/Shanghai'))
      .toContain('09:00')
    expect(formatDateTime('2026-10-21T01:00:00Z', 'UTC'))
      .toContain('01:00')
    expect(formatDateTime('2026-10-21T01:00:00Z', 'Not/AZone'))
      .toBe('2026-10-21T01:00:00Z')
  })
})
