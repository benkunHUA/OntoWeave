import { House, Network, RefreshCw } from 'lucide-react'
import { useMemo, useState } from 'react'
import { SearchField } from '../../../shared/components/form/controls'
import type { OntologyResource, OntologyResources } from '../../../shared/types'
import {
  RESOURCE_COPY,
  RESOURCE_GROUPS,
} from '../lib/constants'
import { bindingLabel, resourceCategory, resourceDirection } from '../lib/format'
import { ResourceCategoryIcon } from './ResourceCategoryIcon'

interface ResourceSidebarProps {
  resources: OntologyResources | null
  activeKey: string
  isLoading: boolean
  error: string | null
  onRetry: () => void
  onSelect: (resource: OntologyResource) => void
  onHome: () => void
}

export function ResourceSidebar({
  resources,
  activeKey,
  isLoading,
  error,
  onRetry,
  onSelect,
  onHome,
}: ResourceSidebarProps) {
  const [query, setQuery] = useState('')
  const allResources = useMemo(
    () => [
      ...(resources?.object_types ?? []),
      ...(resources?.foreign_key_relations ?? []),
      ...(resources?.many_to_many_relations ?? []),
    ],
    [resources],
  )
  const groups = useMemo(() => {
    const keyword = query.trim().toLowerCase()
    return RESOURCE_GROUPS.map((group) => ({
      ...group,
      items: allResources.filter((resource) => {
        if (resourceCategory(resource) !== group.category) return false
        if (!keyword) return true
        return [resource.name, resourceDirection(resource)]
          .join(' ')
          .toLowerCase()
          .includes(keyword)
      }),
    }))
  }, [allResources, query])

  return (
    <aside className="ontology-sidebar">
      <header className="ontology-sidebar__header">
        <div className="brand brand--compact">
          <span className="brand__mark"><Network size={17} /></span>
          <div>
            <strong>OntoWeave</strong>
            <span>{RESOURCE_COPY.appLabel}</span>
          </div>
        </div>
        <button type="button" className="icon-button icon-button--quiet" aria-label="返回首页" onClick={onHome}>
          <House size={17} />
        </button>
      </header>

      <SearchField
        className="ontology-sidebar__search"
        value={query}
        onChange={(event) => setQuery(event.target.value)}
        placeholder={RESOURCE_COPY.searchPlaceholder}
        aria-label={RESOURCE_COPY.searchPlaceholder}
      />

      <nav className="ontology-resource-groups" aria-label="对象与关系">
        {groups.map((group) => (
          <section className="ontology-resource-group" key={group.category}>
            <h2>
              <ResourceCategoryIcon category={group.category} />
              <span>{group.label}</span>
              <em>{group.items.length}</em>
            </h2>
            {group.items.map((resource) => {
              const active = `${resource.kind}:${resource.id}` === activeKey
              return (
                <button
                  type="button"
                  key={`${resource.kind}:${resource.id}`}
                  className={`ontology-resource ${active ? 'is-active' : ''}`}
                  aria-current={active ? 'page' : undefined}
                  onClick={() => onSelect(resource)}
                >
                  <span className="ontology-resource__body">
                    <strong>{resource.name}</strong>
                    <span>{resourceDirection(resource)}</span>
                  </span>
                  <span className="ontology-resource__binding">
                    {bindingLabel(resource.binding)}
                  </span>
                </button>
              )
            })}
          </section>
        ))}
        {isLoading && !allResources.length ? (
          <div className="ontology-sidebar__empty">{RESOURCE_COPY.loading}</div>
        ) : null}
        {!isLoading && !allResources.length ? (
          <div className="ontology-sidebar__empty">
            <strong>{RESOURCE_COPY.emptyTitle}</strong>
            <span>{RESOURCE_COPY.emptyBody}</span>
          </div>
        ) : null}
        {!isLoading && allResources.length && !groups.some((group) => group.items.length) ? (
          <div className="ontology-sidebar__empty">{RESOURCE_COPY.emptySearch}</div>
        ) : null}
        {error ? (
          <div className="ontology-sidebar__error">
            <span>{error}</span>
            <button type="button" onClick={onRetry}>
              <RefreshCw size={13} />
              {RESOURCE_COPY.retry}
            </button>
          </div>
        ) : null}
      </nav>
    </aside>
  )
}
