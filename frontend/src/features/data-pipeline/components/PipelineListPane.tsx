import { ChevronRight, House, Plus, Search, Workflow } from 'lucide-react'
import { useMemo, useState, type FormEvent } from 'react'
import type { Pipeline } from '../../../shared/types'
import { PIPELINE_EMPTY_COPY, PIPELINE_STATUS_LABELS } from '../lib/constants'

interface PipelineListPaneProps {
  pipelines: Pipeline[]
  activeId: string | null
  isLoading: boolean
  error: string | null
  onCreate: (name: string) => Promise<Pipeline | null>
  onSelect: (pipeline: Pipeline) => void
  onHome: () => void
}

export function PipelineListPane({
  pipelines,
  activeId,
  isLoading,
  error,
  onCreate,
  onSelect,
  onHome,
}: PipelineListPaneProps) {
  const [query, setQuery] = useState('')
  const [creating, setCreating] = useState(false)
  const [isCreating, setIsCreating] = useState(false)
  const [name, setName] = useState('新数据流程')

  const filtered = useMemo(() => {
    const keyword = query.trim().toLowerCase()
    if (!keyword) return pipelines
    return pipelines.filter((pipeline) => pipeline.name.toLowerCase().includes(keyword))
  }, [pipelines, query])

  async function submit(event: FormEvent) {
    event.preventDefault()
    const trimmed = name.trim()
    if (!trimmed || isCreating) return
    setIsCreating(true)
    const created = await onCreate(trimmed)
    setIsCreating(false)
    if (created) {
      setCreating(false)
      setName('新数据流程')
    }
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

      {creating ? (
        <form className="pipeline-create-form" onSubmit={(event) => void submit(event)}>
          <input
            autoFocus
            value={name}
            maxLength={200}
            aria-label="流程名称"
            onChange={(event) => setName(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Escape') setCreating(false)
            }}
          />
          <div>
            <button type="button" className="text-button" onClick={() => setCreating(false)}>取消</button>
            <button type="submit" className="primary-button" disabled={isCreating || !name.trim()}>
              {isCreating ? '创建中' : '创建'}
            </button>
          </div>
        </form>
      ) : (
        <button type="button" className="pipeline-new-button" onClick={() => setCreating(true)}>
          <Plus size={16} />
          新建数据整理流程
        </button>
      )}

      <label className="pipeline-search">
        <Search size={14} />
        <input
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="搜索数据整理流程"
          aria-label="搜索数据整理流程"
        />
      </label>

      <nav className="pipeline-list" aria-label="数据整理流程列表">
        {filtered.map((pipeline) => {
          const active = pipeline.id === activeId
          return (
            <button
              type="button"
              key={pipeline.id}
              className={`pipeline-list__item ${active ? 'is-active' : ''}`}
              onClick={() => onSelect(pipeline)}
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
              <ChevronRight size={14} />
            </button>
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
    </aside>
  )
}
