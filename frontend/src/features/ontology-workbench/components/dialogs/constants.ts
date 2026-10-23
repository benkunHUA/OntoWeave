export const GENERATE_DIALOG_COPY = {
  title: '确认全量生成',
  subtitle: '生成会读取当前已发布的数据集版本，并原子切换索引。',
  dataset: '数据集',
  version: '版本',
  latestVersion: '生成时解析最新已发布版本',
  revision: '映射 revision',
  warning: '本次会全量重建该资源及其关联关系。生成期间现有对象继续可查询，成功后才切换。',
  cancel: '取消',
  confirm: '确认生成',
  submitting: '正在提交…',
  failed: '提交生成失败',
} as const

export const REBIND_DIALOG_COPY = {
  title: '确认换源',
  subtitle: '换源只修改草稿数据集，不会立即影响当前生效对象。',
  oldDataset: '当前数据集',
  newDataset: '新数据集',
  warning: '生成成功后才切换到新数据集；在此之前，自动重建仍使用旧生效快照。',
  cancel: '取消',
  confirm: '确认换源',
  submitting: '正在换源…',
  failed: '换源失败',
} as const

export const UNBIND_DIALOG_COPY = {
  title: '确认解绑',
  subtitle: '解绑会停止自动重建，并使当前对象与关系实例不可查询。',
  objectCount: '对象实例',
  relationCount: '关系实例',
  warning: '该动作会删除当前索引数据，且不能撤销。草稿映射和运行记录会保留。',
  acknowledgement: '我确认删除这些对象与关系实例',
  cancel: '取消',
  confirm: '解除绑定',
  submitting: '正在解绑…',
  failed: '解绑失败',
  unit: '条',
} as const

export const CANCEL_RUN_DIALOG_COPY = {
  title: '确认取消运行',
  subtitle: '取消后不会再切换索引，当前生效对象保持不变。',
  queued: '任务尚在排队，确认后立即取消。',
  running: '任务正在运行，确认后会在当前批次结束并清理临时数据后取消。',
  cancel: '返回',
  confirm: '确认取消',
  submitting: '正在取消…',
  failed: '取消运行失败',
} as const

export function activeRunCancelCopy(status: string): string {
  return status === 'QUEUED' ? CANCEL_RUN_DIALOG_COPY.queued : CANCEL_RUN_DIALOG_COPY.running
}
