import { useWorkbenchPaneWidth } from '../../../shared/workbench/useWorkbenchPaneWidth'
import {
  PIPELINE_CHAT_STORAGE_KEY,
  PIPELINE_CHAT_WIDTH,
} from '../lib/constants'

export function usePipelineLayout() {
  return useWorkbenchPaneWidth({
    storageKey: PIPELINE_CHAT_STORAGE_KEY,
    defaultWidth: PIPELINE_CHAT_WIDTH.default,
    min: PIPELINE_CHAT_WIDTH.min,
    max: PIPELINE_CHAT_WIDTH.max,
    cssVariable: '--workbench-center-w',
    resizerSide: 'right',
  })
}
