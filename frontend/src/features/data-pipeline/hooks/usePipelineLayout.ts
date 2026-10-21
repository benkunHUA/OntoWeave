import { useWorkbenchPaneWidth } from '../../../shared/workbench/useWorkbenchPaneWidth'
import {
  PIPELINE_CANVAS_STORAGE_KEY,
  PIPELINE_CANVAS_VAR,
  PIPELINE_CANVAS_WIDTH,
} from '../lib/constants'

export function usePipelineLayout() {
  return useWorkbenchPaneWidth({
    storageKey: PIPELINE_CANVAS_STORAGE_KEY,
    defaultWidth: PIPELINE_CANVAS_WIDTH.default,
    min: PIPELINE_CANVAS_WIDTH.min,
    max: PIPELINE_CANVAS_WIDTH.max,
    cssVariable: PIPELINE_CANVAS_VAR,
  })
}
