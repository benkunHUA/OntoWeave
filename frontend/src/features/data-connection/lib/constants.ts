import type { SyncMode, SyncRunStatus } from '../../../shared/types'

export const DATA_CONNECTION_PATH = '/data-connections'

export type ContextTab = 'overview' | 'tables' | 'datasets'

export const CONTEXT_TABS: Array<{ id: ContextTab; label: string }> = [
  { id: 'overview', label: '概览' },
  { id: 'tables', label: '表与同步' },
  { id: 'datasets', label: '数据集' },
]

export const DEFAULT_MYSQL_PORT = 3306
export const DEFAULT_SYNC_MODE: SyncMode = 'cdc'
export const DEFAULT_VERSION_INTERVAL_SECONDS = 86_400
export const MIN_PORT = 1
export const MAX_PORT = 65_535
export const DATASET_PREVIEW_LIMIT = 100

export const VERSION_INTERVAL_OPTIONS = [
  { value: 3_600, label: '每小时' },
  { value: 21_600, label: '每 6 小时' },
  { value: 43_200, label: '每 12 小时' },
  { value: 86_400, label: '每天' },
]

export const ACTIVE_RUN_STATUSES: SyncRunStatus[] = [
  'queued',
  'preparing',
  'submitted',
  'running',
  'stopping',
]

export const CONTEXT_PANE_WIDTH = {
  default: 480,
  min: 420,
  max: 640,
} as const

export const CONNECTION_LIST_WIDTH = 280
