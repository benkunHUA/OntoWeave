import { ChevronRight, Database, House, Plus, Search } from 'lucide-react'
import { useMemo, useState } from 'react'
import type { DataConnection, DataConnectionState } from '../../../shared/types'
import { CONNECTION_STATUS_LABELS, RUN_STATUS_LABELS, TEST_STATUS_LABELS } from '../lib/labels'

interface ConnectionListPaneProps {
  connections: DataConnection[]
  selectedState: DataConnectionState | null
  activeId: string | null
  isCreating: boolean
  onSelect: (connection: DataConnection) => void
  onCreate: () => void
  onHome: () => void
}

export function ConnectionListPane({
  connections,
  selectedState,
  activeId,
  isCreating,
  onSelect,
  onCreate,
  onHome,
}: ConnectionListPaneProps) {
  const [query, setQuery] = useState('')
  const filtered = useMemo(() => {
    const keyword = query.trim().toLowerCase()
    if (!keyword) return connections
    return connections.filter((connection) => connection.name.toLowerCase().includes(keyword))
  }, [connections, query])

  return (
    <aside className="connection-list-pane">
      <header className="connection-list-pane__header">
        <div className="brand brand--compact">
          <span className="brand__mark">
            <Database size={17} />
          </span>
          <div>
            <strong>OntoWeave</strong>
            <span>数据连接工作台</span>
          </div>
        </div>
        <button type="button" className="icon-button icon-button--quiet" aria-label="返回首页" onClick={onHome}>
          <House size={17} />
        </button>
      </header>

      <button type="button" className="new-connection-button" onClick={onCreate} disabled={isCreating}>
        <Plus size={16} />
        {isCreating ? '正在创建' : '新建数据连接'}
      </button>

      <label className="connection-search">
        <Search size={14} />
        <input
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="搜索数据连接"
          aria-label="搜索数据连接"
        />
      </label>

      <nav className="connection-list" aria-label="数据连接列表">
        {filtered.map((connection) => {
          const isActive = connection.id === activeId
          const latestRun = isActive ? selectedState?.recent_runs[0] : undefined
          return (
            <button
              type="button"
              key={connection.id}
              className={`connection-list__item ${isActive ? 'is-active' : ''}`}
              onClick={() => onSelect(connection)}
            >
              <span className={`connection-list__status is-${connection.status}`} />
              <span className="connection-list__body">
                <span className="connection-list__name">{connection.name}</span>
                <span className="connection-list__meta">
                  {connection.kind.toUpperCase()} · {TEST_STATUS_LABELS[connection.last_test_status]}
                </span>
                <span className="connection-list__meta">
                  最近同步：{latestRun ? RUN_STATUS_LABELS[latestRun.status] : '暂无'}
                </span>
              </span>
              <span className={`connection-list__badge is-${connection.status}`}>
                {CONNECTION_STATUS_LABELS[connection.status]}
              </span>
              <ChevronRight size={14} />
            </button>
          )
        })}
        {!filtered.length ? (
          <div className="connection-list__empty">
            {connections.length ? '没有匹配的连接' : '还没有数据连接'}
          </div>
        ) : null}
      </nav>
    </aside>
  )
}
