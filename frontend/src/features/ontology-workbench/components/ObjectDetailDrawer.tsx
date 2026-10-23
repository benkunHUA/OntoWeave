import { ArrowLeft, ArrowRight, ChevronRight, Database, LoaderCircle, X } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { httpErrorText } from '../../../shared/api/errors'
import {
  getObjectDetail,
  listObjectRelationGroups,
  listObjectRelations,
} from '../../../shared/api/ontology'
import { useEscapeClose } from '../../../shared/hooks/useEscapeClose'
import type {
  ObjectRelationGroup,
  ObjectRelationPage,
  OntologyObjectDetail,
  OntologyProperty,
} from '../../../shared/types'
import { ObjectTable } from './ObjectTable'

interface ObjectDetailDrawerProps {
  objectTypeId: string
  primaryKey: string
  properties: readonly OntologyProperty[]
  onClose: () => void
}

const DETAIL_COPY = {
  title: '对象详情',
  close: '关闭',
  loading: '正在读取对象详情…',
  retry: '重试',
  properties: '属性',
  relations: '关系',
  emptyProperties: '该对象类型还没有属性定义',
  emptyRelations: '暂无关联对象',
  noValue: '—',
  viewAll: '查看全部',
  back: '返回对象详情',
  peerTitle: '对端对象',
  primaryKey: '主键',
  objectTitle: '标题',
  relationPage: '关系分页',
  direction: {
    outgoing: '出向',
    incoming: '入向',
    both: '双向',
  },
} as const

function displayValue(value: unknown): string {
  if (value === null || value === undefined || value === '') return DETAIL_COPY.noValue
  if (Array.isArray(value)) return value.length ? value.map(displayValue).join('、') : DETAIL_COPY.noValue
  if (typeof value === 'boolean') return value ? '是' : '否'
  if (typeof value === 'object') {
    try {
      return JSON.stringify(value)
    } catch {
      return String(value)
    }
  }
  return String(value)
}

export function ObjectDetailDrawer({
  objectTypeId,
  primaryKey,
  properties,
  onClose,
}: ObjectDetailDrawerProps) {
  const [detail, setDetail] = useState<OntologyObjectDetail | null>(null)
  const [groups, setGroups] = useState<ObjectRelationGroup[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [groupsError, setGroupsError] = useState<string | null>(null)
  const [selectedGroup, setSelectedGroup] = useState<ObjectRelationGroup | null>(null)
  const [relationPage, setRelationPage] = useState<ObjectRelationPage | null>(null)
  const [relationPageNumber, setRelationPageNumber] = useState(1)
  const [relationLoading, setRelationLoading] = useState(false)
  const [relationError, setRelationError] = useState<string | null>(null)
  useEscapeClose(true, onClose)

  const loadDetail = useCallback(async () => {
    setIsLoading(true)
    setError(null)
    try {
      setDetail(await getObjectDetail(objectTypeId, primaryKey))
    } catch (reason) {
      setError(httpErrorText(reason, '读取对象详情失败'))
    } finally {
      setIsLoading(false)
    }
  }, [objectTypeId, primaryKey])

  useEffect(() => {
    void loadDetail()
  }, [loadDetail])

  useEffect(() => {
    let cancelled = false
    setGroupsError(null)
    void listObjectRelationGroups(objectTypeId, primaryKey)
      .then((next) => {
        if (!cancelled) setGroups(next)
      })
      .catch((reason: unknown) => {
        if (!cancelled) setGroupsError(httpErrorText(reason, '读取关联对象失败'))
      })
    return () => {
      cancelled = true
    }
  }, [objectTypeId, primaryKey])

  const loadRelationPage = useCallback(async (group: ObjectRelationGroup, page: number) => {
    setRelationLoading(true)
    setRelationError(null)
    try {
      const direction = group.direction === 'incoming' ? 'incoming' : 'outgoing'
      setRelationPage(await listObjectRelations(objectTypeId, group.relation_id, {
        primary_key: primaryKey,
        direction,
        page,
        page_size: 20,
      }))
      setRelationPageNumber(page)
    } catch (reason) {
      setRelationError(httpErrorText(reason, '读取关系分页失败'))
    } finally {
      setRelationLoading(false)
    }
  }, [objectTypeId, primaryKey])

  function openGroup(group: ObjectRelationGroup) {
    setSelectedGroup(group)
    setRelationPage(null)
    setRelationPageNumber(1)
    void loadRelationPage(group, 1)
  }

  function backToDetail() {
    setSelectedGroup(null)
    setRelationPage(null)
    setRelationError(null)
  }

  const title = detail?.title || primaryKey
  const detailProperties = detail?.properties ?? {}

  return (
    <aside className="ontology-detail-drawer" role="dialog" aria-modal="true" aria-label={DETAIL_COPY.title}>
      <header className="ontology-detail-drawer__head">
        <div className="ontology-detail-drawer__identity">
          {selectedGroup ? (
            <button type="button" className="icon-button icon-button--quiet" aria-label={DETAIL_COPY.back} onClick={backToDetail}>
              <ArrowLeft size={15} />
            </button>
          ) : (
            <span className="ontology-detail-drawer__icon"><Database size={16} /></span>
          )}
          <div>
            <strong>{selectedGroup ? selectedGroup.label || DETAIL_COPY.peerTitle : title}</strong>
            <span>{selectedGroup ? `${DETAIL_COPY.peerTitle} · ${selectedGroup.total} 个` : primaryKey}</span>
          </div>
        </div>
        <button type="button" className="icon-button icon-button--quiet" aria-label={DETAIL_COPY.close} onClick={onClose}>
          <X size={16} />
        </button>
      </header>

      {selectedGroup ? (
        <div className="ontology-detail-drawer__body">
          <ObjectTable
            ariaLabel={DETAIL_COPY.relationPage}
            columns={[
              { key: 'primaryKey', header: DETAIL_COPY.primaryKey, render: (item) => item.primary_key },
              { key: 'title', header: DETAIL_COPY.objectTitle, render: (item) => item.title || DETAIL_COPY.noValue },
            ]}
            items={relationPage?.items ?? []}
            total={relationPage?.total ?? selectedGroup.total}
            page={relationPageNumber}
            pageSize={relationPage?.page_size ?? 20}
            getRowKey={(item) => item.primary_key}
            onPageChange={(page) => void loadRelationPage(selectedGroup, page)}
            isLoading={relationLoading}
            error={relationError}
            onRetry={() => void loadRelationPage(selectedGroup, relationPageNumber)}
            emptyTitle="暂无对端对象"
            emptyBody="生成关系实例后会显示在这里"
          />
        </div>
      ) : (
        <div className="ontology-detail-drawer__body">
          {isLoading ? (
            <p className="ontology-detail-drawer__state"><LoaderCircle size={15} className="is-spinning" /> {DETAIL_COPY.loading}</p>
          ) : null}
          {error ? (
            <div className="ontology-data-state is-error" role="alert">
              <span>{error}</span>
              <button type="button" className="secondary-button" onClick={() => void loadDetail()}>{DETAIL_COPY.retry}</button>
            </div>
          ) : null}

          {detail ? (
            <>
              <section className="ontology-detail-section">
                <h3>{DETAIL_COPY.properties}</h3>
                {properties.length ? (
                  <dl className="ontology-detail-properties">
                    {properties.map((property) => (
                      <div key={property.id}>
                        <dt>
                          {property.name}
                          {property.is_primary_key ? <em>PK</em> : null}
                        </dt>
                        <dd>{displayValue(detailProperties[property.id])}</dd>
                      </div>
                    ))}
                  </dl>
                ) : <p className="ontology-detail-empty">{DETAIL_COPY.emptyProperties}</p>}
              </section>

              <section className="ontology-detail-section">
                <h3>{DETAIL_COPY.relations}</h3>
                {groupsError ? <p className="ontology-detail-error" role="alert">{groupsError}</p> : null}
                {!groupsError && !groups.length ? <p className="ontology-detail-empty">{DETAIL_COPY.emptyRelations}</p> : null}
                <div className="ontology-relation-groups">
                  {groups.map((group) => (
                    <article className="ontology-relation-group" key={`${group.relation_id}-${group.direction}`}>
                      <header>
                        <div>
                          <strong>{group.label || group.relation_id}</strong>
                          <span>
                            {group.direction === 'incoming'
                              ? <ArrowLeft size={11} />
                              : group.direction === 'outgoing'
                                ? <ArrowRight size={11} />
                                : null}
                            {group.direction_label || DETAIL_COPY.direction[group.direction]}
                            {' · '}{group.total} 个
                          </span>
                        </div>
                        <button type="button" className="text-button" onClick={() => openGroup(group)}>
                          {DETAIL_COPY.viewAll} <ChevronRight size={12} />
                        </button>
                      </header>
                      <ul>
                        {group.items.slice(0, 5).map((item) => (
                          <li key={item.primary_key}>
                            <span>{item.title || item.primary_key}</span>
                            <em>{item.primary_key}</em>
                          </li>
                        ))}
                      </ul>
                    </article>
                  ))}
                </div>
              </section>
            </>
          ) : null}
        </div>
      )}
    </aside>
  )
}
