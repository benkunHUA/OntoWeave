import type {
  ConnectionStatus,
  ConnectionTestStatus,
  MissingStep,
  SyncMode,
  SyncRunStatus,
} from '../../../shared/types'

export const CONNECTION_STATUS_LABELS: Record<ConnectionStatus, string> = {
  draft: '草稿',
  active: '已启用',
}

export const TEST_STATUS_LABELS: Record<ConnectionTestStatus, string> = {
  untested: '未测试',
  success: '测试通过',
  failed: '测试失败',
}

export const SYNC_MODE_LABELS: Record<SyncMode, string> = {
  cdc: 'CDC 持续同步',
  snapshot: '快照同步',
}

export const RUN_STATUS_LABELS: Record<SyncRunStatus, string> = {
  queued: '排队中',
  preparing: '准备中',
  submitted: '已提交',
  running: '运行中',
  stopping: '停止中',
  succeeded: '成功',
  stopped: '已停止',
  failed: '失败',
}

export const MISSING_STEP_LABELS: Record<MissingStep, string> = {
  SAVE_CONNECTION: '保存连接配置',
  TEST_CONNECTION: '测试连接',
  SELECT_TABLES: '选择数据表',
  CREATE_SYNC: '创建同步',
  RUN_SYNC: '开始同步',
}

export const EVENT_TYPE_LABELS: Record<string, string> = {
  'connection.draft.created': '创建连接草稿',
  'connection.updated': '保存连接配置',
  'connection.tested': '测试连接',
  'sync.tasks.saved': '保存表选择',
  'sync.run.queued': '进入同步队列',
  'sync.run.submitted': '提交 Flink 作业',
  'sync.run.running': '同步运行中',
  'sync.run.snapshot_completed': '初始化快照完成',
  'sync.run.stopping': '请求停止',
  'sync.run.stopped': '同步已停止',
  'sync.run.succeeded': '同步完成',
  'sync.run.failed': '同步失败',
  'dataset.version.published': '发布数据集版本',
}

export const UNKNOWN_EVENT_LABEL = '数据操作'

export const PREVIEW_MODE_LABELS = {
  version: '版本数据',
  live: '最新数据',
  refresh: '刷新',
} as const
