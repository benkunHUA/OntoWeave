import { describe, expect, it } from 'vitest'
import type { ConversationEvent } from '../../../shared/types'
import { isVisiblePipelineEvent } from './eventVisibility'

function event(eventType: string): ConversationEvent {
  return {
    id: eventType,
    event_id: eventType,
    operation_id: null,
    run_id: null,
    turn_id: null,
    event_type: eventType,
    summary: eventType,
    payload: {},
    created_at: '2026-01-01T00:00:00Z',
  }
}

describe('isVisiblePipelineEvent', () => {
  it('hides high-frequency draft change events', () => {
    expect(isVisiblePipelineEvent(event('pipeline.draft.changed'))).toBe(false)
  })

  it('keeps user-facing business events', () => {
    expect(isVisiblePipelineEvent(event('pipeline.build.succeeded'))).toBe(true)
  })
})
