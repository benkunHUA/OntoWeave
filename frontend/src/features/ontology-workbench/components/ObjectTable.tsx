import { ChevronLeft, ChevronRight, LoaderCircle, RefreshCw } from 'lucide-react'
import type { KeyboardEvent, ReactNode } from 'react'
import { useEffect, useMemo, useState } from 'react'

export interface ObjectTableColumn<T> {
  key: string
  header: ReactNode
  render: (item: T) => ReactNode
  className?: string
}

interface ObjectTableProps<T> {
  ariaLabel: string
  columns: readonly ObjectTableColumn<T>[]
  items: readonly T[]
  total: number
  page: number
  pageSize: number
  getRowKey: (item: T) => string
  onPageChange: (page: number) => void
  isLoading?: boolean
  error?: string | null
  onRetry?: () => void
  onRowClick?: (item: T) => void
  emptyTitle: string
  emptyBody: string
  versionNotice?: string | null
  onDismissVersionNotice?: () => void
  loadingText?: string
  totalLabel?: string
}

const PAGE_COPY = {
  previous: '上一页',
  next: '下一页',
  jump: '跳至',
  page: '页',
  total: (total: number) => `共 ${total} 条`,
  loading: '正在读取数据…',
  retry: '重试',
  refreshing: '刷新中…',
} as const

function clickableRowProps(onRowClick: (() => void) | undefined) {
  if (!onRowClick) return {}
  return {
    tabIndex: 0,
    role: 'button',
    onClick: onRowClick,
    onKeyDown: (event: KeyboardEvent<HTMLTableRowElement>) => {
      if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault()
        onRowClick()
      }
    },
  }
}

export function ObjectTable<T>({
  ariaLabel,
  columns,
  items,
  total,
  page,
  pageSize,
  getRowKey,
  onPageChange,
  isLoading = false,
  error = null,
  onRetry,
  onRowClick,
  emptyTitle,
  emptyBody,
  versionNotice = null,
  onDismissVersionNotice,
  loadingText = PAGE_COPY.loading,
  totalLabel,
}: ObjectTableProps<T>) {
  const pageCount = Math.max(1, Math.ceil(total / Math.max(1, pageSize)))
  const [pageInput, setPageInput] = useState(String(page))

  useEffect(() => setPageInput(String(page)), [page])

  const clampedPage = useMemo(() => Math.min(page, pageCount), [page, pageCount])

  function commitPageInput() {
    const parsed = Number.parseInt(pageInput, 10)
    if (!Number.isFinite(parsed)) {
      setPageInput(String(page))
      return
    }
    const next = Math.min(Math.max(1, parsed), pageCount)
    setPageInput(String(next))
    if (next !== page) onPageChange(next)
  }

  return (
    <div className="ontology-object-table">
      {versionNotice ? (
        <div className="ontology-data-notice" role="status">
          <span>{versionNotice}</span>
          {onDismissVersionNotice ? (
            <button type="button" onClick={onDismissVersionNotice}>知道了</button>
          ) : null}
        </div>
      ) : null}

      {error ? (
        <div className="ontology-data-state is-error" role="alert">
          <strong>数据读取失败</strong>
          <span>{error}</span>
          {onRetry ? (
            <button type="button" className="secondary-button" onClick={onRetry}>
              <RefreshCw size={13} /> {PAGE_COPY.retry}
            </button>
          ) : null}
        </div>
      ) : (
        <div className="ontology-object-table__scroll">
          <table aria-label={ariaLabel}>
            <thead>
              <tr>
                {columns.map((column) => (
                  <th key={column.key} className={column.className} scope="col">{column.header}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {isLoading && !items.length ? (
                <tr>
                  <td colSpan={columns.length} className="ontology-object-table__state">
                    <LoaderCircle size={16} className="is-spinning" /> {loadingText}
                  </td>
                </tr>
              ) : null}
              {!isLoading && !items.length ? (
                <tr>
                  <td colSpan={columns.length} className="ontology-object-table__state">
                    <strong>{emptyTitle}</strong>
                    <span>{emptyBody}</span>
                  </td>
                </tr>
              ) : null}
              {items.map((item) => (
                <tr
                  key={getRowKey(item)}
                  className={onRowClick ? 'is-clickable' : undefined}
                  {...clickableRowProps(onRowClick ? () => onRowClick(item) : undefined)}
                >
                  {columns.map((column) => (
                    <td key={column.key} className={column.className}>{column.render(item)}</td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
          {isLoading && items.length ? (
            <div className="ontology-object-table__refreshing">
              <LoaderCircle size={13} className="is-spinning" /> {PAGE_COPY.refreshing}
            </div>
          ) : null}
        </div>
      )}

      <footer className="ontology-object-table__footer">
        <span>{totalLabel ?? PAGE_COPY.total(total)}</span>
        <div className="ontology-object-table__pager">
          <button
            type="button"
            className="icon-button icon-button--quiet"
            aria-label={PAGE_COPY.previous}
            disabled={isLoading || clampedPage <= 1}
            onClick={() => onPageChange(clampedPage - 1)}
          >
            <ChevronLeft size={15} />
          </button>
          <label>
            {PAGE_COPY.jump}
            <input
              aria-label="页码"
              inputMode="numeric"
              value={pageInput}
              onChange={(event) => setPageInput(event.target.value)}
              onBlur={commitPageInput}
              onKeyDown={(event) => {
                if (event.key === 'Enter') commitPageInput()
              }}
              disabled={isLoading}
            />
            / {pageCount} {PAGE_COPY.page}
          </label>
          <button
            type="button"
            className="icon-button icon-button--quiet"
            aria-label={PAGE_COPY.next}
            disabled={isLoading || clampedPage >= pageCount}
            onClick={() => onPageChange(clampedPage + 1)}
          >
            <ChevronRight size={15} />
          </button>
        </div>
      </footer>
    </div>
  )
}
