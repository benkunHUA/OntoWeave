import type { ConversationEvent } from '../../../shared/types'

const HIDDEN_EVENT_PREFIXES = [
  'pipeline.agent.turn',
  'pipeline.agent.question',
  'pipeline.draft.changed',
]

/** 只用于驱动画布状态的事件不进入对话时间线。 */
export function isVisiblePipelineEvent(event: ConversationEvent): boolean {
  return !HIDDEN_EVENT_PREFIXES.some((prefix) => event.event_type.startsWith(prefix))
}
