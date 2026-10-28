import { Badge, Button, Group, Loader, Stack, Table, Text } from '@mantine/core'
import { Ban, History, RefreshCw, RotateCcw } from 'lucide-react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { httpErrorText } from '../../../../shared/api/errors'
import {
  cancelIndexRun,
  generateObjectBinding,
  generateRelationBinding,
  getIndexRun,
  listObjectBindingRuns,
  listRelationBindingRuns,
} from '../../../../shared/api/ontology'
import type { BindingRead, IndexRun } from '../../../../shared/types'
import { RUN_POLL_INTERVAL_MS, RUN_STATUS_LABELS, isActiveRun } from '../../lib/constants'
import { CancelRunDialog } from '../dialogs/CancelRunDialog'
import { GenerateDialog } from '../dialogs/GenerateDialog'
import { RunDetailDrawer } from '../dialogs/RunDetailDrawer'
import { EmptyState } from '../../../../shared/components/EmptyState'
import { RUN_TRIGGER_LABELS, formatRunTime, runCounts } from '../dialogs/runFormat'
import type { RunsTabProps } from './types'

const RUN_LIST_LIMIT = 50
const RUNS_COPY = {
  title: '运行记录',
  refresh: '刷新',
  loading: '正在读取运行记录…',
  emptyTitle: '还没有运行记录',
  emptyBody: '完成绑定并生成后会显示在这里。',
  status: '状态',
  trigger: '触发方式',
  sourceVersion: '来源版本',
  started: '开始',
  finished: '结束',
  written: '写入',
  warnings: '告警',
  skipped: '跳过',
  actions: '操作',
  cancel: '取消',
  retry: '重试',
  retryDataset: '当前草稿数据集',
} as const

function listRuns(resource: RunsTabProps['resource']): Promise<IndexRun[]> {
  return resource.kind === 'object_type'
    ? listObjectBindingRuns(resource.id, RUN_LIST_LIMIT)
    : listRelationBindingRuns(resource.id, RUN_LIST_LIMIT)
}

function generate(resource: RunsTabProps['resource'], revision: number) {
  const payload = { mapping_revision: revision }
  const operationId = crypto.randomUUID()
  return resource.kind === 'object_type'
    ? generateObjectBinding(resource.id, payload, operationId)
    : generateRelationBinding(resource.id, payload, operationId)
}

function bindingDatasetLabel(binding: BindingRead | null): string {
  return binding?.draft_dataset_id || binding?.active_dataset_id || RUNS_COPY.retryDataset
}

function retryRevision(run: IndexRun, binding: BindingRead | null): number {
  return binding?.revision ?? run.mapping_revision
}

export function RunsTab({ resource, run, isLoading, error, onRefresh }: RunsTabProps) {
  const [runs, setRuns] = useState<IndexRun[]>(() => (run ? [run] : []))
  const [listLoading, setListLoading] = useState(true)
  const [listError, setListError] = useState<string | null>(null)
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [cancelTarget, setCancelTarget] = useState<IndexRun | null>(null)
  const [cancelSubmitting, setCancelSubmitting] = useState(false)
  const [cancelError, setCancelError] = useState<string | null>(null)
  const [retryTarget, setRetryTarget] = useState<IndexRun | null>(null)
  const [retrySubmitting, setRetrySubmitting] = useState(false)
  const [retryError, setRetryError] = useState<string | null>(null)

  const loadRuns = useCallback(async (silent = false) => {
    if (!silent) setListLoading(true)
    setListError(null)
    try {
      setRuns(await listRuns(resource))
    } catch (reason) {
      setListError(httpErrorText(reason, '读取运行记录失败'))
    } finally {
      if (!silent) setListLoading(false)
    }
  }, [resource])

  useEffect(() => {
    setSelectedId(null)
    setRuns([])
    void loadRuns()
  }, [loadRuns])

  const activeKey = useMemo(
    () => runs.filter(isActiveRun).map((item) => `${item.id}:${item.status}:${item.phase ?? ''}`).join('|'),
    [runs],
  )

  useEffect(() => {
    if (!activeKey) return
    const timer = window.setInterval(() => void loadRuns(true), RUN_POLL_INTERVAL_MS)
    return () => window.clearInterval(timer)
  }, [activeKey, loadRuns])

  const selectedRun = runs.find((item) => item.id === selectedId) ?? null
  const displayError = listError ?? error

  function refreshAll() {
    void loadRuns()
    onRefresh()
  }

  function requestCancel(target: IndexRun) {
    if (!isActiveRun(target)) return
    setCancelError(null)
    setCancelTarget(target)
  }

  async function confirmCancel() {
    if (!cancelTarget) return
    setCancelSubmitting(true)
    setCancelError(null)
    try {
      const next = await cancelIndexRun(cancelTarget.id, crypto.randomUUID())
      setRuns((current) => current.map((item) => (item.id === next.id ? next : item)))
      setCancelTarget(null)
      onRefresh()
      await loadRuns(true)
    } catch (reason) {
      setCancelError(httpErrorText(reason, '取消运行失败'))
    } finally {
      setCancelSubmitting(false)
    }
  }

  function requestRetry(target: IndexRun) {
    setRetryError(null)
    setRetryTarget(target)
  }

  async function confirmRetry() {
    if (!retryTarget) return
    setRetrySubmitting(true)
    setRetryError(null)
    try {
      const response = await generate(resource, retryRevision(retryTarget, resource.binding))
      const next = await getIndexRun(response.run_id).catch(() => null)
      setRetryTarget(null)
      if (next) {
        setRuns((current) => [next, ...current.filter((item) => item.id !== next.id)])
        setSelectedId(next.id)
      }
      onRefresh()
      await loadRuns(true)
    } catch (reason) {
      setRetryError(httpErrorText(reason, '提交重试失败'))
    } finally {
      setRetrySubmitting(false)
    }
  }

  return (
    <section className="ontology-runs-panel" aria-label={`${resource.name} 运行记录`}>
      <header className="ontology-runs-panel__head">
        <Group gap={6}>
          <History size={16} />
          <Text component="strong" size="sm">{RUNS_COPY.title}</Text>
          <Text size="xs" c="dimmed">{resource.name}</Text>
        </Group>
        <Button
          type="button"
          variant="default"
          size="xs"
          leftSection={<RefreshCw size={13} />}
          onClick={refreshAll}
          disabled={listLoading || isLoading}
        >
          {RUNS_COPY.refresh}
        </Button>
      </header>

      {displayError ? <Text className="ontology-data-inline-error" size="xs" c="red" role="alert">{displayError}</Text> : null}

      {listLoading && !runs.length ? (
        <Group className="ontology-run-list-state" gap="xs"><Loader size="sm" /> <Text size="xs">{RUNS_COPY.loading}</Text></Group>
      ) : null}
      {!listLoading && !runs.length ? (
        <EmptyState className="ontology-run-list-state" title={RUNS_COPY.emptyTitle} body={RUNS_COPY.emptyBody} />
      ) : null}

      {runs.length ? (
        <div className="ontology-run-table-wrap">
          <Table className="ontology-run-table" aria-label={`${resource.name} 运行列表`}>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>{RUNS_COPY.status}</Table.Th>
                <Table.Th>{RUNS_COPY.trigger}</Table.Th>
                <Table.Th>{RUNS_COPY.sourceVersion}</Table.Th>
                <Table.Th>{RUNS_COPY.started} / {RUNS_COPY.finished}</Table.Th>
                <Table.Th>{RUNS_COPY.written}</Table.Th>
                <Table.Th>{RUNS_COPY.warnings} / {RUNS_COPY.skipped}</Table.Th>
                <Table.Th>{RUNS_COPY.actions}</Table.Th>
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {runs.map((item) => {
                const counts = runCounts(item)
                const active = isActiveRun(item)
                return (
                  <Table.Tr key={item.id} onClick={() => setSelectedId(item.id)} tabIndex={0}>
                    <Table.Td>
                      <Badge color={item.status === 'SUCCEEDED' ? 'teal' : item.status === 'FAILED' ? 'red' : 'gray'} variant="light">
                        {RUN_STATUS_LABELS[item.status]}
                      </Badge>
                    </Table.Td>
                    <Table.Td>{RUN_TRIGGER_LABELS[item.trigger_type]}</Table.Td>
                    <Table.Td><code title={item.target_dataset_version_id}>{item.target_dataset_version_id.slice(0, 8)}</code></Table.Td>
                    <Table.Td className="ontology-run-time">
                      <Stack gap={0}><span>{formatRunTime(item.started_at)}</span><span>{formatRunTime(item.finished_at)}</span></Stack>
                    </Table.Td>
                    <Table.Td>{counts.written}</Table.Td>
                    <Table.Td>{counts.warnings} / {counts.skipped}</Table.Td>
                    <Table.Td className="ontology-run-actions">
                      <Button
                        type="button"
                        variant="subtle"
                        size="xs"
                        disabled={!active}
                        leftSection={<Ban size={12} />}
                        onClick={(event) => {
                          event.stopPropagation()
                          requestCancel(item)
                        }}
                      >
                        {RUNS_COPY.cancel}
                      </Button>
                      {item.status === 'FAILED' ? (
                        <Button
                          type="button"
                          variant="subtle"
                          size="xs"
                          leftSection={<RotateCcw size={12} />}
                          onClick={(event) => {
                            event.stopPropagation()
                            requestRetry(item)
                          }}
                        >
                          {RUNS_COPY.retry}
                        </Button>
                      ) : null}
                    </Table.Td>
                  </Table.Tr>
                )
              })}
            </Table.Tbody>
          </Table>
        </div>
      ) : null}

      {selectedRun ? (
        <RunDetailDrawer
          run={selectedRun}
          onClose={() => setSelectedId(null)}
          onCancel={requestCancel}
          onRetry={requestRetry}
        />
      ) : null}

      {cancelTarget ? (
        <CancelRunDialog
          runId={cancelTarget.id}
          status={cancelTarget.status}
          isSubmitting={cancelSubmitting}
          error={cancelError}
          onCancel={() => setCancelTarget(null)}
          onConfirm={() => void confirmCancel()}
        />
      ) : null}

      {retryTarget ? (
        <GenerateDialog
          resourceName={resource.name}
          datasetLabel={bindingDatasetLabel(resource.binding)}
          datasetVersionLabel={retryTarget.target_dataset_version_id}
          revision={retryRevision(retryTarget, resource.binding)}
          isSubmitting={retrySubmitting}
          error={retryError}
          onCancel={() => setRetryTarget(null)}
          onConfirm={() => void confirmRetry()}
        />
      ) : null}
    </section>
  )
}
