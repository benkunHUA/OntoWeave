export interface ConversationEvent {
  id: string
  event_id: string
  operation_id: string | null
  run_id: string | null
  turn_id: string | null
  event_type: string
  summary: string
  payload: Record<string, unknown>
  created_at: string
}
