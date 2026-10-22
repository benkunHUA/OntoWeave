export const BUILD_COPY = {
  build: '构建',
  creating: '创建中',
  preparing: '保存草稿中',
  rebuild: '重新构建',
  confirmTitle: '确认构建',
  confirmSubtitle: '冻结当前草稿并执行全部输出',
  revision: '草稿修订',
  outputs: '本次输出',
  frozenHint: '构建期间修改草稿不会进入本次构建',
  confirm: '确认构建',
  cancel: '取消',
  noOutputs: '请先配置至少一个输出节点',
  noBoundOutputs: '请先绑定全部输出节点',
  blockingIssues: '存在阻塞问题，修复后才能构建',
  savingDraft: '正在保存草稿，请稍候',
  prepareFailed: '草稿保存失败，未创建构建',
  viewDetails: '查看构建详情',
  detailsTitle: '构建详情',
  outputTitle: '输出',
  diagnostics: '错误与诊断',
  noDiagnostics: '本次输出没有错误信息',
  cancelBuild: '取消构建',
  cancelSending: '正在发送取消请求…',
  cancelRequested: '已发送取消请求，等待 worker 确认',
  cancelFailed: '取消构建失败，请重试',
  noBuild: '尚未创建构建',
  schedule: '调度',
  logOpen: '打开完整日志',
  logMissing: '日志尚未持久化，以下为诊断信息',
  outputDataset: '查看输出数据集',
} as const

export const PREVIEW_COPY = {
  title: '节点预览',
  refresh: '刷新预览',
  close: '关闭预览',
  cancel: '取消预览',
  currentDraft: '基于当前草稿',
  stale: '草稿已修改，预览已过期',
  loading: '正在执行预览…',
  inputs: '输入版本',
  errors: '预览错误',
  noRows: '没有返回行',
} as const

export const SCHEDULE_COPY = {
  title: '定时调度',
  subtitle: '调度始终使用已发布版本',
  missing: '未创建调度',
  name: '调度名称',
  frequency: '频率',
  customCron: '自定义 cron',
  timezone: 'IANA 时区',
  nextRun: '下次运行',
  lastRun: '上次运行',
  history: '运行历史',
  emptyHistory: '还没有调度运行记录',
  create: '创建调度',
  save: '保存修改',
  pause: '暂停调度',
  resume: '恢复调度',
  confirmCreate: '确认创建',
  confirmSave: '确认保存',
  confirmPause: '确认暂停',
  confirmResume: '确认恢复',
  createTitle: '创建调度',
  saveTitle: '修改调度',
  pauseTitle: '暂停调度',
  resumeTitle: '恢复调度',
  createBody: '将按所选 cron 和时区创建定时运行。',
  saveBody: '保存后将按新配置计算下一次运行，不会补跑历史触发。',
  pauseBody: '暂停会清理待执行触发，但不会取消正在运行的构建。',
  resumeBody: '恢复后从现在开始计算下一次运行，不会补跑暂停期间。',
  buildLink: '查看构建',
} as const

export const SOURCE_COPY = {
  title: '输出来源',
  output: '输出版本',
  build: '构建',
  pipelineVersion: '流程版本',
  directInputs: '直接输入',
  noInputs: '没有记录直接输入',
  loading: '正在读取来源…',
  missing: '元数据不可用',
  restricted: '元数据无权限',
  buildUnavailable: '构建信息不可用',
  buildDetails: '查看构建详情',
  buildLog: '打开构建日志',
  logMissing: '日志尚未持久化',
} as const

export const OUTPUT_BUILD_COPY = {
  ariaLabel: '最近构建',
  title: '最近构建',
  missingOutput: '最近构建未包含此输出',
  elapsed: '耗时',
  log: '打开构建日志',
} as const

export const OUTPUT_DATASET_COPY = {
  title: '输出数据集详情',
  datasetId: '数据集 ID',
  datasetVersionId: '数据版本 ID',
  preview: '发布数据',
  rows: '行',
  close: '关闭',
} as const

export const PIPELINE_PREVIEW_ROW_LIMIT = 100
export const PIPELINE_DATASET_PREVIEW_LIMIT = 100

export const BUILD_ACTIVE_STATUSES = new Set(['queued', 'running'])
