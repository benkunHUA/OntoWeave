import {
  Alert,
  Button,
  Group,
  Loader,
  SegmentedControl,
  Table,
  Text,
  Title,
  UnstyledButton,
} from '@mantine/core'
import { ArrowLeft, Database, KeyRound, Maximize2, RefreshCw } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { getDataset, getDatasetPreview } from '../../../../shared/api'
import type { Dataset, DatasetPreview } from '../../../../shared/types'
import { DatasetPreviewTable } from '../../../../shared/components/DatasetPreviewTable'
import { EmptyState } from '../../../../shared/components/EmptyState'
import { DATASET_PREVIEW_LIMIT } from '../../lib/constants'
import { DatasetPreviewDialog } from '../../dialogs/DatasetPreviewDialog'
import { schemaFields } from '../../lib/datasetSchema'
import { formatBytes, formatDateTime, formatRows } from '../../lib/format'
import { PREVIEW_MODE_LABELS } from '../../lib/labels'

interface DatasetDetailProps {
  datasetId: string
  connectionName: string
  onBack: () => void
}

export function DatasetDetail({ datasetId, connectionName, onBack }: DatasetDetailProps) {
  const [dataset, setDataset] = useState<Dataset | null>(null)
  const [versionId, setVersionId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [preview, setPreview] = useState<DatasetPreview | null>(null)
  const [previewError, setPreviewError] = useState<string | null>(null)
  const [previewLoading, setPreviewLoading] = useState(false)
  const [live, setLive] = useState(false)
  const [refreshToken, setRefreshToken] = useState(0)
  const [previewOpen, setPreviewOpen] = useState(false)

  useEffect(() => {
    let active = true
    void (async () => {
      setDataset(null)
      setError(null)
      setLive(false)
      try {
        const value = await getDataset(datasetId)
        if (!active) return
        setDataset(value)
        setVersionId(value.current_version_id)
      } catch (reason) {
        if (active) setError(reason instanceof Error ? reason.message : '读取数据集失败')
      }
    })()
    return () => {
      active = false
    }
  }, [datasetId])

  useEffect(() => {
    if (!live && !versionId) {
      return
    }
    let active = true
    void (async () => {
      setPreview(null)
      setPreviewError(null)
      setPreviewLoading(true)
      try {
        const value = await getDatasetPreview(
          datasetId,
          live ? null : versionId,
          DATASET_PREVIEW_LIMIT,
          live,
        )
        if (active) setPreview(value)
      } catch (reason) {
        if (active) setPreviewError(reason instanceof Error ? reason.message : '读取数据预览失败')
      } finally {
        if (active) setPreviewLoading(false)
      }
    })()
    return () => {
      active = false
    }
  }, [datasetId, versionId, live, refreshToken])

  const version = useMemo(
    () => dataset?.versions.find((item) => item.id === versionId) ?? dataset?.current_version ?? null,
    [dataset, versionId],
  )
  const fields = schemaFields(version)

  return (
    <div className="dataset-detail">
      <Button
        type="button"
        className="back-button"
        variant="subtle"
        size="compact-sm"
        leftSection={<ArrowLeft size={14} />}
        onClick={onBack}
      >
        返回数据集列表
      </Button>

      {error ? <Alert color="red" variant="light" title="读取数据集失败">{error}</Alert> : null}
      {!dataset && !error ? <EmptyState icon={<Loader size="sm" />} title="正在读取数据集…" /> : null}

      {dataset ? (
        <>
          <section className="context-section">
            <Group gap={6}><Database size={16} /><Title order={3}>{dataset.name}</Title></Group>
            <dl className="detail-grid">
              <div><dt>当前版本</dt><dd>{dataset.current_version ? `v${dataset.current_version.version_number}` : '—'}</dd></div>
              <div><dt>版本数量</dt><dd>{dataset.version_count || dataset.versions.length}</dd></div>
              <div><dt>来源连接</dt><dd>{connectionName}</dd></div>
              <div><dt>源表</dt><dd>{dataset.source_schema && dataset.source_table ? `${dataset.source_schema}.${dataset.source_table}` : '—'}</dd></div>
              <div><dt>行数</dt><dd>{formatRows(version?.row_count)}</dd></div>
              <div><dt>列数</dt><dd>{version?.column_count ?? '—'}</dd></div>
              <div><dt>文件大小</dt><dd>{formatBytes(version?.byte_size)}</dd></div>
              <div><dt>发布时间</dt><dd>{formatDateTime(version?.published_at ?? version?.committed_at)}</dd></div>
              <div><dt>创建时间</dt><dd>{formatDateTime(dataset.created_at)}</dd></div>
              <div><dt>更新时间</dt><dd>{formatDateTime(dataset.updated_at)}</dd></div>
            </dl>
          </section>

          <section className="context-section">
            <Title order={3}>字段</Title>
            {fields.length ? (
              <div className="schema-table-wrap">
                <Table className="schema-table">
                  <Table.Thead><Table.Tr><Table.Th>字段</Table.Th><Table.Th>类型</Table.Th><Table.Th>必填</Table.Th><Table.Th>说明</Table.Th></Table.Tr></Table.Thead>
                  <Table.Tbody>
                    {fields.map((field) => (
                      <Table.Tr key={field.name}>
                        <Table.Td>{field.name} {field.required ? <KeyRound size={11} /> : null}</Table.Td>
                        <Table.Td><code>{field.type}</code></Table.Td>
                        <Table.Td>{field.required ? '是' : '否'}</Table.Td>
                        <Table.Td>{field.doc ?? '—'}</Table.Td>
                      </Table.Tr>
                    ))}
                  </Table.Tbody>
                </Table>
              </div>
            ) : <EmptyState title="这个版本没有可展示的字段结构" />}
          </section>

          <section className="context-section">
            <Title order={3}>版本历史</Title>
            <ul className="version-list">
              {dataset.versions.map((item) => (
                <li key={item.id}>
                  <UnstyledButton
                    type="button"
                    className={item.id === version?.id ? 'is-active' : ''}
                    onClick={() => {
                      setLive(false)
                      setVersionId(item.id)
                    }}
                  >
                    <Group gap={5} wrap="nowrap">
                      <Text component="span" size="sm" fw={600}>v{item.version_number}</Text>
                      {item.id === dataset.current_version_id ? <Text component="em" size="xs" c="brand">当前</Text> : null}
                    </Group>
                    <Text component="span" size="xs" c="dimmed">{formatRows(item.row_count)} 行 · {item.column_count} 列</Text>
                    <Text component="span" size="xs" c="dimmed">{item.source_run_id ? `运行 ${item.source_run_id.slice(0, 8)}` : '来源运行 —'}</Text>
                    <Text component="span" size="xs" c="dimmed">{formatDateTime(item.published_at ?? item.committed_at)}</Text>
                  </UnstyledButton>
                </li>
              ))}
            </ul>
          </section>

          <section className="context-section">
            <div className="context-section__heading">
              <Title order={3}>数据预览</Title>
              <Group className="preview-actions" gap="xs" wrap="nowrap">
                <SegmentedControl
                  size="xs"
                  value={live ? 'live' : 'version'}
                  disabled={!version && !live}
                  onChange={(value) => setLive(value === 'live')}
                  data={[
                    { value: 'version', label: PREVIEW_MODE_LABELS.version, disabled: !version },
                    { value: 'live', label: PREVIEW_MODE_LABELS.live },
                  ]}
                />
                <Button
                  type="button"
                  variant="default"
                  size="compact-xs"
                  leftSection={<RefreshCw size={13} />}
                  onClick={() => setRefreshToken((value) => value + 1)}
                >
                  {PREVIEW_MODE_LABELS.refresh}
                </Button>
                <Button
                  type="button"
                  variant="default"
                  size="compact-xs"
                  leftSection={<Maximize2 size={13} />}
                  onClick={() => setPreviewOpen(true)}
                  disabled={!preview}
                >
                  放大
                </Button>
              </Group>
            </div>
            {live || version ? (
              <DatasetPreviewTable
                preview={preview}
                loading={previewLoading}
                error={previewError}
                limit={DATASET_PREVIEW_LIMIT}
              />
            ) : <EmptyState title="还没有已发布版本" />}
          </section>
        </>
      ) : null}

      {previewOpen ? (
        <DatasetPreviewDialog
          title={dataset ? `${dataset.name} · ${live ? '实时预览' : `v${version?.version_number ?? '—'}`}` : ''}
          preview={preview}
          loading={previewLoading}
          error={previewError}
          onClose={() => setPreviewOpen(false)}
        />
      ) : null}
    </div>
  )
}
