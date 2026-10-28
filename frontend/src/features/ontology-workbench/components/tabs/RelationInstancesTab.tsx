import { Button, Group, TextInput } from '@mantine/core'
import { ArrowRight, Search } from 'lucide-react'
import { useCallback, useEffect, useRef, useState } from 'react'
import { httpErrorText } from '../../../../shared/api/errors'
import { getOntologyResourceDefinition, listRelationInstances } from '../../../../shared/api/ontology'
import type {
  OntologyResourceDefinition,
  RelationInstance,
  RelationInstancePage,
} from '../../../../shared/types'
import { ObjectTable } from '../ObjectTable'
import type { ResourceTabProps } from './types'

const PAGE_SIZE = 20
const INSTANCE_COPY = {
  loading: '正在读取关系实例…',
  emptyTitle: '还没有关系实例',
  emptyBody: '生成关系后会显示两端对象的主键。',
  from: '来源对象',
  to: '目标对象',
  fromKey: '来源主键',
  toKey: '目标主键',
  search: '查询',
  clear: '清空条件',
  relation: '关系',
} as const

type InstanceWithTitles = RelationInstance & {
  from_title?: string | null
  to_title?: string | null
  fromTitle?: string | null
  toTitle?: string | null
}

function sideTitle(row: InstanceWithTitles, side: 'from' | 'to'): string | null {
  return side === 'from'
    ? row.from_title ?? row.fromTitle ?? null
    : row.to_title ?? row.toTitle ?? null
}

export function RelationInstancesTab({ resource }: ResourceTabProps) {
  const [definition, setDefinition] = useState<OntologyResourceDefinition | null>(null)
  const [fromDraft, setFromDraft] = useState('')
  const [toDraft, setToDraft] = useState('')
  const [fromKey, setFromKey] = useState('')
  const [toKey, setToKey] = useState('')
  const [page, setPage] = useState(1)
  const [data, setData] = useState<RelationInstancePage | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const requestRef = useRef(0)

  useEffect(() => {
    let cancelled = false
    void getOntologyResourceDefinition('relation', resource.id)
      .then((next) => {
        if (!cancelled) setDefinition(next)
      })
      .catch(() => {
        if (!cancelled) setDefinition(null)
      })
    return () => {
      cancelled = true
    }
  }, [resource.id])

  const load = useCallback(async () => {
    const requestId = ++requestRef.current
    setIsLoading(true)
    setError(null)
    try {
      const next = await listRelationInstances(resource.id, {
        page,
        page_size: PAGE_SIZE,
        from_primary_key: fromKey || undefined,
        to_primary_key: toKey || undefined,
      })
      if (requestRef.current === requestId) setData(next)
    } catch (reason) {
      if (requestRef.current === requestId) {
        setError(httpErrorText(reason, '读取关系实例失败'))
      }
    } finally {
      if (requestRef.current === requestId) setIsLoading(false)
    }
  }, [fromKey, page, resource.id, toKey])

  useEffect(() => {
    void load()
    return () => {
      requestRef.current += 1
    }
  }, [load])

  function applyFilters() {
    setFromKey(fromDraft.trim())
    setToKey(toDraft.trim())
    setPage(1)
  }

  function clearFilters() {
    setFromDraft('')
    setToDraft('')
    setFromKey('')
    setToKey('')
    setPage(1)
  }

  const fromName = definition?.source_type_name || INSTANCE_COPY.from
  const toName = definition?.target_type_name || INSTANCE_COPY.to

  return (
    <section className="ontology-data-panel" aria-label={`${resource.name} 关系实例`}>
      <div className="ontology-instance-heading">
        <span className="eyebrow">{INSTANCE_COPY.relation}</span>
        <strong>{fromName} <ArrowRight size={13} /> {toName}</strong>
        <span>{resource.name}</span>
      </div>

      <form
        className="ontology-instance-filters"
        onSubmit={(event) => {
          event.preventDefault()
          applyFilters()
        }}
      >
        <TextInput
          label={INSTANCE_COPY.fromKey}
          value={fromDraft}
          placeholder={fromName}
          onChange={(event) => setFromDraft(event.currentTarget.value)}
          size="xs"
        />
        <span className="ontology-instance-filters__arrow"><ArrowRight size={14} /></span>
        <TextInput
          label={INSTANCE_COPY.toKey}
          value={toDraft}
          placeholder={toName}
          onChange={(event) => setToDraft(event.currentTarget.value)}
          size="xs"
        />
        <Group gap="xs" align="flex-end">
          <Button type="submit" size="xs" leftSection={<Search size={13} />} disabled={isLoading}>
            {INSTANCE_COPY.search}
          </Button>
          <Button type="button" variant="default" size="xs" onClick={clearFilters} disabled={isLoading}>
            {INSTANCE_COPY.clear}
          </Button>
        </Group>
      </form>

      <ObjectTable
        ariaLabel={`${resource.name} 关系实例列表`}
        columns={[
          {
            key: 'from',
            header: <>{INSTANCE_COPY.from} · {fromName}</>,
            render: (item: InstanceWithTitles) => (
              <span className="ontology-instance-cell">
                <code>{item.from_primary_key}</code>
                <em>{sideTitle(item, 'from') || '—'}</em>
              </span>
            ),
          },
          {
            key: 'direction',
            header: '',
            className: 'ontology-instance-direction',
            render: () => <ArrowRight size={14} />,
          },
          {
            key: 'to',
            header: <>{INSTANCE_COPY.to} · {toName}</>,
            render: (item: InstanceWithTitles) => (
              <span className="ontology-instance-cell">
                <code>{item.to_primary_key}</code>
                <em>{sideTitle(item, 'to') || '—'}</em>
              </span>
            ),
          },
        ]}
        items={(data?.items ?? []) as InstanceWithTitles[]}
        total={data?.total ?? 0}
        page={page}
        pageSize={PAGE_SIZE}
        getRowKey={(item) => `${item.from_primary_key}\u0000${item.to_primary_key}`}
        onPageChange={setPage}
        isLoading={isLoading}
        error={error}
        onRetry={() => void load()}
        emptyTitle={INSTANCE_COPY.emptyTitle}
        emptyBody={INSTANCE_COPY.emptyBody}
        loadingText={INSTANCE_COPY.loading}
      />
    </section>
  )
}
