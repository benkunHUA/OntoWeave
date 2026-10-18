import { ArrowLeft, Database, KeyRound } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { getDataset, getDatasetPreview } from '../../../../shared/api'
import type { Dataset, DatasetPreview } from '../../../../shared/types'
import { DATASET_PREVIEW_LIMIT } from '../../lib/constants'
import { schemaFields } from '../../lib/datasetSchema'
import { formatBytes, formatDateTime, formatRows } from '../../lib/format'
import { DatasetPreviewTable } from './DatasetPreviewTable'

interface DatasetDetailProps {
  datasetId: string
  onBack: () => void
}

export function DatasetDetail({ datasetId, onBack }: DatasetDetailProps) {
  const [dataset, setDataset] = useState<Dataset | null>(null)
  const [versionId, setVersionId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [preview, setPreview] = useState<DatasetPreview | null>(null)
  const [previewError, setPreviewError] = useState<string | null>(null)
  const [previewLoading, setPreviewLoading] = useState(false)

  useEffect(() => {
    let active = true
    setDataset(null)
    setError(null)
    void getDataset(datasetId)
      .then((value) => {
        if (!active) return
        setDataset(value)
        setVersionId(value.current_version_id)
      })
      .catch((reason: unknown) => {
        if (active) setError(reason instanceof Error ? reason.message : '读取数据集失败')
      })
    return () => {
      active = false
    }
  }, [datasetId])

  useEffect(() => {
    if (!versionId) {
      setPreview(null)
      setPreviewError(null)
      setPreviewLoading(false)
      return
    }
    let active = true
    setPreview(null)
    setPreviewError(null)
    setPreviewLoading(true)
    void getDatasetPreview(datasetId, versionId, DATASET_PREVIEW_LIMIT)
      .then((value) => {
        if (active) setPreview(value)
      })
      .catch((reason: unknown) => {
        if (active) setPreviewError(reason instanceof Error ? reason.message : '读取数据预览失败')
      })
      .finally(() => {
        if (active) setPreviewLoading(false)
      })
    return () => {
      active = false
    }
  }, [datasetId, versionId])

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
              <div><dt>版本数量</dt><dd>{dataset.versions.length}</dd></div>
              <div><dt>行数</dt><dd>{formatRows(version?.row_count)}</dd></div>
              <div><dt>列数</dt><dd>{version?.column_count ?? '—'}</dd></div>
              <div><dt>文件大小</dt><dd>{formatBytes(version?.byte_size)}</dd></div>
              <div><dt>发布时间</dt><dd>{formatDateTime(version?.published_at ?? version?.committed_at)}</dd></div>
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
                    onClick={() => setVersionId(item.id)}
                  >
                    <span>v{item.version_number}</span>
                    <span>{formatRows(item.row_count)} 行</span>
                    <span>{formatDateTime(item.published_at ?? item.committed_at)}</span>
                    {item.id === dataset.current_version_id ? <em>当前</em> : null}
                  </button>
                </li>
              ))}
            </ul>
          </section>

          <section className="context-section">
            <h2>数据预览</h2>
            {version ? (
              <DatasetPreviewTable preview={preview} loading={previewLoading} error={previewError} />
            ) : <div className="empty-panel">还没有已发布版本</div>}
          </section>
        </>
      ) : null}
    </div>
  )
}
