import type { JsonObject, PipelineDraftCommand } from '../../../shared/types'
import { buildDraftCommand, type DraftCommandPayload } from './command'

export interface OutputDraftInput {
  name: string
  writeMode?: string
  primaryKeyColumns?: string[]
  expectations?: JsonObject[]
}

export interface ExpectationDraft extends JsonObject {
  type: string
}

export function setOutputPayload(nodeId: string, output: OutputDraftInput): DraftCommandPayload {
  return {
    type: 'set_output',
    output: {
      node_id: nodeId,
      name: output.name.trim(),
      write_mode: output.writeMode ?? 'snapshot',
      primary_key_columns: output.primaryKeyColumns ?? [],
      expectations: output.expectations ?? [],
    },
  }
}

export function buildSetOutputCommand(
  nodeId: string,
  output: OutputDraftInput,
  baseRevision: number,
  operationId: string,
  turnId?: string,
): PipelineDraftCommand {
  return buildDraftCommand(setOutputPayload(nodeId, output), baseRevision, operationId, turnId)
}
