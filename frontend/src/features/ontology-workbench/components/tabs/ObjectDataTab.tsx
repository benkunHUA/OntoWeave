import { Text, TextInput } from '@mantine/core'
import { Search } from 'lucide-react'
import { useCallback, useEffect, useRef, useState } from 'react'
import { httpErrorText } from '../../../../shared/api/errors'
import { getOntologyResourceDefinition, listObjects } from '../../../../shared/api/ontology'
import type {
  FilterCondition,
  OntologyObjectPage,
  OntologyProperty,
  OntologyResourceDefinition,
} from '../../../../shared/types'
import { FilterBar } from '../FilterBar'
import { ObjectDetailDrawer } from '../ObjectDetailDrawer'
import { ObjectTable } from '../ObjectTable'
import type { ResourceTabProps } from './types'

const PAGE_SIZE = 20
const SEARCH_DEBOUNCE_MS = 300
const OBJECT_DATA_COPY = {
  searchLabel: '搜索对象',
  searchPlaceholder: '搜索主键或标题',
  versionChanged: '对象已更新，已回到第 1 页',
  loading: '正在读取对象数据…',
  emptyTitle: '还没有对象实例',
  emptyBody: '完成映射并生成后，对象会显示在这里。',
  primaryKey: '主键',
  title: '标题',
  definitionError: '读取对象属性定义失败',
} as const

export function ObjectDataTab({ resource }: ResourceTabProps) {
  const [definition, setDefinition] = useState<OntologyResourceDefinition | null>(null)
  const [properties, setProperties] = useState<OntologyProperty[]>([])
  const [definitionError, setDefinitionError] = useState<string | null>(null)
  const [page, setPage] = useState(1)
  const [searchInput, setSearchInput] = useState('')
  const [searchQuery, setSearchQuery] = useState('')
  const [filters, setFilters] = useState<FilterCondition[]>([])
  const [data, setData] = useState<OntologyObjectPage | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [versionNotice, setVersionNotice] = useState(false)
  const [currentVersion, setCurrentVersion] = useState<string | null>(null)
  const [selectedPrimaryKey, setSelectedPrimaryKey] = useState<string | null>(null)
  const lastVersionRef = useRef<string | null>(null)
  const requestRef = useRef(0)

  useEffect(() => {
    let cancelled = false
    setDefinitionError(null)
    void getOntologyResourceDefinition('object_type', resource.id)
      .then((next) => {
        if (cancelled) return
        setDefinition(next)
        setProperties(next.properties)
      })
      .catch((reason: unknown) => {
        if (!cancelled) setDefinitionError(httpErrorText(reason, OBJECT_DATA_COPY.definitionError))
      })
    return () => {
      cancelled = true
    }
  }, [resource.id])

  useEffect(() => {
    if (searchInput === searchQuery) return
    const timer = window.setTimeout(() => {
      setSearchQuery(searchInput)
      setPage(1)
    }, SEARCH_DEBOUNCE_MS)
    return () => window.clearTimeout(timer)
  }, [searchInput, searchQuery])

  const load = useCallback(async () => {
    const requestId = ++requestRef.current
    setIsLoading(true)
    setError(null)
    try {
      const next = await listObjects(resource.id, {
        page,
        page_size: PAGE_SIZE,
        q: searchQuery.trim() || undefined,
        filters,
      })
      if (requestRef.current !== requestId) return
      const previous = lastVersionRef.current
      if (previous && next.index_version_id && previous !== next.index_version_id) {
        setVersionNotice(true)
        setPage(1)
      }
      if (next.index_version_id) {
        lastVersionRef.current = next.index_version_id
        setCurrentVersion(next.index_version_id)
      }
      setData(next)
    } catch (reason) {
      if (requestRef.current === requestId) {
        setError(httpErrorText(reason, '读取对象数据失败'))
      }
    } finally {
      if (requestRef.current === requestId) setIsLoading(false)
    }
  }, [filters, page, resource.id, searchQuery])

  useEffect(() => {
    void load()
    return () => {
      requestRef.current += 1
    }
  }, [load])

  function applyFilters(next: FilterCondition[]) {
    setFilters(next)
    setPage(1)
  }

  return (
    <section className="ontology-data-panel" aria-label={`${resource.name} 对象数据`}>
      <div className="ontology-data-toolbar">
        <TextInput
          className="ontology-data-search"
          type="search"
          aria-label={OBJECT_DATA_COPY.searchLabel}
          placeholder={OBJECT_DATA_COPY.searchPlaceholder}
          leftSection={<Search size={14} />}
          value={searchInput}
          onChange={(event) => setSearchInput(event.currentTarget.value)}
          size="xs"
        />
        <Text className="ontology-data-toolbar__summary" size="xs" c="dimmed">
          {resource.name} · {OBJECT_DATA_COPY.primaryKey} / {OBJECT_DATA_COPY.title}
        </Text>
      </div>

      <FilterBar
        key={resource.id}
        properties={properties}
        value={filters}
        onChange={applyFilters}
        disabled={isLoading && !data}
      />

      {definitionError ? <Text className="ontology-data-inline-error" size="xs" c="red" role="alert">{definitionError}</Text> : null}

      <ObjectTable
        ariaLabel={`${resource.name} 对象列表`}
        columns={[
          {
            key: 'primaryKey',
            header: OBJECT_DATA_COPY.primaryKey,
            render: (item) => <code>{item.primary_key}</code>,
          },
          {
            key: 'title',
            header: OBJECT_DATA_COPY.title,
            render: (item) => item.title || '—',
          },
        ]}
        items={data?.items ?? []}
        total={data?.total ?? 0}
        page={page}
        pageSize={PAGE_SIZE}
        getRowKey={(item) => item.primary_key}
        onPageChange={setPage}
        onRowClick={(item) => setSelectedPrimaryKey(item.primary_key)}
        isLoading={isLoading}
        error={error}
        onRetry={() => void load()}
        emptyTitle={OBJECT_DATA_COPY.emptyTitle}
        emptyBody={OBJECT_DATA_COPY.emptyBody}
        loadingText={OBJECT_DATA_COPY.loading}
        versionNotice={versionNotice ? OBJECT_DATA_COPY.versionChanged : null}
        onDismissVersionNotice={() => setVersionNotice(false)}
      />

      {definition ? (
        <Text className="ontology-data-panel__meta" size="xs" c="dimmed">
          属性定义 {definition.properties.length} 个 · 当前索引版本 {currentVersion?.slice(0, 8) || '—'}
        </Text>
      ) : null}

      {selectedPrimaryKey ? (
        <ObjectDetailDrawer
          objectTypeId={resource.id}
          primaryKey={selectedPrimaryKey}
          properties={properties}
          onClose={() => setSelectedPrimaryKey(null)}
        />
      ) : null}
    </section>
  )
}
