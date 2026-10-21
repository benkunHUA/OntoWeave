import type { PipelineDraftCommand } from '../../../shared/types'

export type DraftCommandPayload = Omit<PipelineDraftCommand, 'base_revision' | 'operation_id'>

export function buildDraftCommand(
  payload: DraftCommandPayload,
  baseRevision: number,
  operationId: string,
  turnId: string = crypto.randomUUID(),
): PipelineDraftCommand {
  return {
    ...payload,
    base_revision: baseRevision,
    operation_id: operationId,
    turn_id: turnId,
  }
}
