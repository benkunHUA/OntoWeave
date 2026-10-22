import { House, Plus, Trash2, Workflow } from 'lucide-react'
import { useMemo, useState } from 'react'
import { SearchField } from '../../../shared/components/form/controls'
import type { Pipeline } from '../../../shared/types'
import {
  PIPELINE_DELETE_COPY,
  PIPELINE_EMPTY_COPY,
  PIPELINE_NEW_COPY,
  PIPELINE_STATUS_LABELS,
} from '../lib/constants'
import { DeletePipelineDialog } from './dialogs/DeletePipelineDialog'

interface PipelineListPaneProps {
  pipelines: Pipeline[]
  activeId: string | null
  isLoading: boolean
  error: string | null
  onCreate: () => Promise<Pipeline | null>
  onDelete: (pipeline: Pipeline) => Promise<boolean>
  onSelect: (pipeline: Pipeline) => void
  onHome: () => void
}

export function PipelineListPane({
  pipelines,
  activeId,
  isLoading,
  error,
  onCreate,
  onDelete,
  onSelect,
  onHome,
}: PipelineListPaneProps) {
  const [query, setQuery] = useState('')
  const [isCreating, setIsCreating] = useState(false)
  const [pendingDelete, setPendingDelete] = useState<Pipeline | null>(null)
  const [isDeleting, setIsDeleting] = useState(false)

  const filtered = useMemo(() => {
    const keyword = query.trim().toLowerCase()
    if (!keyword) return pipelines
    return pipelines.filter((pipeline) => pipeline.name.toLowerCase().includes(keyword))
  }, [pipelines, query])

  async function create() {
    if (isCreating) return
    setIsCreating(true)
    await onCreate()
    setIsCreating(false)
  }

  async function confirmDelete() {
    if (!pendingDelete || isDeleting) return
    setIsDeleting(true)
    const deleted = await onDelete(pendingDelete)
    setIsDeleting(false)
    if (deleted) setPendingDelete(null)
  }

  return (
    <aside className="pipeline-list-pane">
      <header className="pipeline-list-pane__header">
        <div className="brand brand--compact">
          <span className="brand__mark"><Workflow size={17} /></span>
          <div>
            <strong>OntoWeave</strong>
            <span>数据整理工作台</span>
          </div>
        </div>
        <button type="button" className="icon-button icon-button--quiet" aria-label="返回首页" onClick={onHome}>
          <House size={17} />
        </button>
      </header>

      <button
        type="button"
        className="pipeline-new-button"
        onClick={() => void create()}
        disabled={isCreating}
      >
        <Plus size={16} />
        {isCreating ? PIPELINE_NEW_COPY.creating : PIPELINE_NEW_COPY.create}
      </button>

      <SearchField
        className="pipeline-search"
        value={query}
        onChange={(event) => setQuery(event.target.value)}
        placeholder="搜索数据整理流程"
        aria-label="搜索数据整理流程"
      />

      <nav className="pipeline-list" aria-label="数据整理流程列表">
        {filtered.map((pipeline) => {
          const active = pipeline.id === activeId
          return (
            <div
              key={pipeline.id}
              className={`pipeline-list__item ${active ? 'is-active' : ''}`}
            >
              <button
                type="button"
                className="pipeline-list__select"
                onClick={() => onSelect(pipeline)}
                aria-current={active ? 'page' : undefined}
              >
                <span className={`pipeline-list__status is-${pipeline.status}`} />
                <span className="pipeline-list__body">
                  <span className="pipeline-list__name">{pipeline.name}</span>
                  <span className="pipeline-list__meta">
                    {pipeline.current_version_id ? '已发布版本可用' : '尚未发布'}
                  </span>
                </span>
                <span className={`pipeline-list__badge is-${pipeline.status}`}>
                  {PIPELINE_STATUS_LABELS[pipeline.status] ?? pipeline.status}
                </span>
              </button>
              <button
                type="button"
                className="pipeline-list__delete"
                aria-label={`${PIPELINE_DELETE_COPY.action}：${pipeline.name}`}
                onClick={() => setPendingDelete(pipeline)}
              >
                <Trash2 size={14} />
              </button>
            </div>
          )
        })}
        {isLoading ? <div className="pipeline-list__empty">正在读取流程…</div> : null}
        {!isLoading && !filtered.length ? (
          <div className="pipeline-list__empty">
            <strong>{PIPELINE_EMPTY_COPY.list}</strong>
            <span>{PIPELINE_EMPTY_COPY.listHint}</span>
          </div>
        ) : null}
        {error ? <div className="pipeline-list__error">{error}</div> : null}
      </nav>
      {pendingDelete ? (
        <DeletePipelineDialog
          name={pendingDelete.name}
          isDeleting={isDeleting}
          onCancel={() => setPendingDelete(null)}
          onConfirm={() => void confirmDelete()}
        />
      ) : null}
    </aside>
  )
}
