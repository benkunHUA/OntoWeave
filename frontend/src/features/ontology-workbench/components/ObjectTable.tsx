import {
  ActionIcon,
  Alert,
  Button,
  Group,
  Loader,
  Stack,
  Table,
  Text,
  TextInput,
} from '@mantine/core'
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
        <Alert className="ontology-data-notice" color="blue" variant="light" role="status">
          <Group justify="space-between" gap="sm">
            <Text size="xs">{versionNotice}</Text>
            {onDismissVersionNotice ? (
              <Button type="button" variant="subtle" size="xs" onClick={onDismissVersionNotice}>知道了</Button>
            ) : null}
          </Group>
        </Alert>
      ) : null}

      {error ? (
        <Alert className="ontology-data-state" color="red" variant="light" title="数据读取失败" role="alert">
          <Stack gap="sm">
            <Text size="xs">{error}</Text>
            {onRetry ? (
              <Button
                type="button"
                variant="default"
                size="xs"
                leftSection={<RefreshCw size={13} />}
                onClick={onRetry}
                w="fit-content"
              >
                {PAGE_COPY.retry}
              </Button>
            ) : null}
          </Stack>
        </Alert>
      ) : (
        <div className="ontology-object-table__scroll">
          <Table aria-label={ariaLabel}>
            <Table.Thead>
              <Table.Tr>
                {columns.map((column) => (
                  <Table.Th key={column.key} className={column.className} scope="col">{column.header}</Table.Th>
                ))}
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {isLoading && !items.length ? (
                <Table.Tr>
                  <Table.Td colSpan={columns.length} className="ontology-object-table__state">
                    <Group gap="xs" justify="center"><Loader size="sm" /> <Text size="xs">{loadingText}</Text></Group>
                  </Table.Td>
                </Table.Tr>
              ) : null}
              {!isLoading && !items.length ? (
                <Table.Tr>
                  <Table.Td colSpan={columns.length} className="ontology-object-table__state">
                    <Stack gap={2} align="center">
                      <Text component="strong" size="sm">{emptyTitle}</Text>
                      <Text component="span" size="xs" c="dimmed">{emptyBody}</Text>
                    </Stack>
                  </Table.Td>
                </Table.Tr>
              ) : null}
              {items.map((item) => (
                <Table.Tr
                  key={getRowKey(item)}
                  className={onRowClick ? 'is-clickable' : undefined}
                  {...clickableRowProps(onRowClick ? () => onRowClick(item) : undefined)}
                >
                  {columns.map((column) => (
                    <Table.Td key={column.key} className={column.className}>{column.render(item)}</Table.Td>
                  ))}
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
          {isLoading && items.length ? (
            <div className="ontology-object-table__refreshing">
              <LoaderCircle size={13} className="is-spinning" /> {PAGE_COPY.refreshing}
            </div>
          ) : null}
        </div>
      )}

      <footer className="ontology-object-table__footer">
        <Text size="xs" c="dimmed">{totalLabel ?? PAGE_COPY.total(total)}</Text>
        <Group className="ontology-object-table__pager" gap="xs">
          <ActionIcon
            type="button"
            variant="subtle"
            color="gray"
            size="sm"
            aria-label={PAGE_COPY.previous}
            disabled={isLoading || clampedPage <= 1}
            onClick={() => onPageChange(clampedPage - 1)}
          >
            <ChevronLeft size={15} />
          </ActionIcon>
          <Group gap={4} wrap="nowrap">
            <Text size="xs" c="dimmed">{PAGE_COPY.jump}</Text>
            <TextInput
              aria-label="页码"
              inputMode="numeric"
              value={pageInput}
              onChange={(event) => setPageInput(event.target.value)}
              onBlur={commitPageInput}
              onKeyDown={(event) => {
                if (event.key === 'Enter') commitPageInput()
              }}
              disabled={isLoading}
              size="xs"
              w={54}
            />
            <Text size="xs" c="dimmed">/ {pageCount} {PAGE_COPY.page}</Text>
          </Group>
          <ActionIcon
            type="button"
            variant="subtle"
            color="gray"
            size="sm"
            aria-label={PAGE_COPY.next}
            disabled={isLoading || clampedPage >= pageCount}
            onClick={() => onPageChange(clampedPage + 1)}
          >
            <ChevronRight size={15} />
          </ActionIcon>
        </Group>
      </footer>
    </div>
  )
}
