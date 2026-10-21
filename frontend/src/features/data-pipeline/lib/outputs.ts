import type { PipelineDraftResponse } from '../../../shared/types'

export interface PipelineOutputRef {
  nodeId: string
  outputId: string | null
  name: string
}

export function pipelineOutputs(draft: PipelineDraftResponse | null): PipelineOutputRef[] {
  return draft?.definition.nodes
    .filter((node) => node.type === 'output')
    .map((node) => ({
      nodeId: node.id,
      outputId: typeof node.config.outputId === 'string' ? node.config.outputId : null,
      name: node.name,
    })) ?? []
}

export function outputNamesById(draft: PipelineDraftResponse | null): Record<string, string> {
  return Object.fromEntries(
    pipelineOutputs(draft)
      .filter((output): output is PipelineOutputRef & { outputId: string } => Boolean(output.outputId))
      .map((output) => [output.outputId, output.name]),
  )
}

export function shortId(value: string): string {
  return value.length > 12 ? `${value.slice(0, 8)}…` : value
}
