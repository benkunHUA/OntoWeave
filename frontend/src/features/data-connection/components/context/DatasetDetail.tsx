import { ArrowLeft, Database, KeyRound, Maximize2 } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { getDataset, getDatasetPreview } from '../../../../shared/api'
import type { Dataset, DatasetPreview } from '../../../../shared/types'
import { DATASET_PREVIEW_LIMIT } from '../../lib/constants'
import { DatasetPreviewDialog } from '../../dialogs/DatasetPreviewDialog'
import { schemaFields } from '../../lib/datasetSchema'
import { formatBytes, formatDateTime, formatRows } from '../../lib/format'
import { PREVIEW_MODE_LABELS } from '../../lib/labels'
import { DatasetPreviewTable } from './DatasetPreviewTable'

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
      <button type="button" className="back-button" onClick={onBack}>
        <ArrowLeft size={14} />
        返回数据集列表
      </button>

      {error ? <div className="inline-alert is-error">{error}</div> : null}
      {!dataset && !error ? <div className="empty-panel">正在读取数据集…</div> : null}

      {dataset ? (
        <>
          <section className="context-section">
            <h2><Database size={16} /> {dataset.name}</h2>
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
            <h2>字段</h2>
            {fields.length ? (
              <div className="schema-table-wrap">
                <table className="schema-table">
                  <thead><tr><th>字段</th><th>类型</th><th>必填</th><th>说明</th></tr></thead>
                  <tbody>
                    {fields.map((field) => (
                      <tr key={field.name}>
                        <td>{field.name} {field.required ? <KeyRound size={11} /> : null}</td>
                        <td><code>{field.type}</code></td>
                        <td>{field.required ? '是' : '否'}</td>
                        <td>{field.doc ?? '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : <div className="empty-panel">这个版本没有可展示的字段结构</div>}
          </section>

          <section className="context-section">
            <h2>版本历史</h2>
            <ul className="version-list">
              {dataset.versions.map((item) => (
                <li key={item.id}>
                  <button
                    type="button"
                    className={item.id === version?.id ? 'is-active' : ''}
                    onClick={() => {
                      setLive(false)
                      setVersionId(item.id)
                    }}
                  >
                    <span>v{item.version_number}</span>
                    <span>{formatRows(item.row_count)} 行 · {item.column_count} 列</span>
                    <span>{item.source_run_id ? `运行 ${item.source_run_id.slice(0, 8)}` : '来源运行 —'}</span>
                    <span>{formatDateTime(item.published_at ?? item.committed_at)}</span>
                    {item.id === dataset.current_version_id ? <em>当前</em> : null}
                  </button>
                </li>
              ))}
            </ul>
          </section>

          <section className="context-section">
            <div className="context-section__heading">
              <h2>数据预览</h2>
              <div className="preview-actions">
                <button
                  type="button"
                  className={!live ? 'is-active' : ''}
                  disabled={!version}
                  onClick={() => setLive(false)}
                >
                  {PREVIEW_MODE_LABELS.version}
                </button>
                <button
                  type="button"
                  className={live ? 'is-active' : ''}
                  onClick={() => setLive(true)}
                >
                  {PREVIEW_MODE_LABELS.live}
                </button>
                <button type="button" onClick={() => setRefreshToken((value) => value + 1)}>
                  {PREVIEW_MODE_LABELS.refresh}
                </button>
                <button type="button" onClick={() => setPreviewOpen(true)} disabled={!preview}>
                  <Maximize2 size={13} />
                  放大
                </button>
              </div>
            </div>
            {live || version ? (
              <DatasetPreviewTable preview={preview} loading={previewLoading} error={previewError} />
            ) : <div className="empty-panel">还没有已发布版本</div>}
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
