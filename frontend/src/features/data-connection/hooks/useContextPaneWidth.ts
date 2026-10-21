import { useWorkbenchPaneWidth } from '../../../shared/workbench/useWorkbenchPaneWidth'
import { CONTEXT_PANE_WIDTH } from '../lib/constants'

const STORAGE_KEY = 'ontoweave.data-connection.context-pane-width'

export function useContextPaneWidth() {
  return useWorkbenchPaneWidth({
    storageKey: STORAGE_KEY,
    defaultWidth: CONTEXT_PANE_WIDTH.default,
    min: CONTEXT_PANE_WIDTH.min,
    max: CONTEXT_PANE_WIDTH.max,
    cssVariable: '--context-pane-w',
  })
}
