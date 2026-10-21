export type PipelinePanelState =
  | { mode: 'none' }
  | { mode: 'config'; nodeId: string; focusField: string | null }
  | { mode: 'preview'; nodeId: string }

export function closePipelinePanel(): PipelinePanelState {
  return { mode: 'none' }
}

export function openNodePanel(
  _current: PipelinePanelState,
  nodeId: string,
): PipelinePanelState {
  return { mode: 'config', nodeId, focusField: null }
}

export function openPreviewPanel(
  _current: PipelinePanelState,
  nodeId: string,
): PipelinePanelState {
  return { mode: 'preview', nodeId }
}

export function openIssuePanel(
  _current: PipelinePanelState,
  nodeId: string,
  field: string | null,
): PipelinePanelState {
  return { mode: 'config', nodeId, focusField: field }
}
