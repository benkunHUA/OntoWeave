import type { IndexRun, OntologyResource } from '../../../shared/types'
import {
  RESOURCE_CATEGORY_LABELS,
  RESOURCE_STATUS_LABELS,
  RUN_STATUS_LABELS,
  type ResourceTabId,
} from '../lib/constants'
import { resourceCategory, resourceDirection } from '../lib/format'
import { ObjectDataTab } from './tabs/ObjectDataTab'
import { ObjectMappingTab } from './tabs/ObjectMappingTab'
import { RelationInstancesTab } from './tabs/RelationInstancesTab'
import { RelationMappingTab } from './tabs/RelationMappingTab'
import { RelationOverviewTab } from './tabs/RelationOverviewTab'
import { ResourceTabBar } from './tabs/ResourceTabBar'
import { RunsTab } from './tabs/RunsTab'

interface ResourceWorkspaceProps {
  resource: OntologyResource
  tabs: readonly { id: ResourceTabId; label: string }[]
  activeTabId: ResourceTabId
  onTabChange: (tabId: ResourceTabId) => void
  run: IndexRun | null
  isRunLoading: boolean
  runError: string | null
  onRefreshRun: () => void
}

export function ResourceWorkspace({
  resource,
  tabs,
  activeTabId,
  onTabChange,
  run,
  isRunLoading,
  runError,
  onRefreshRun,
}: ResourceWorkspaceProps) {
  const category = resourceCategory(resource)
  const selected = tabs.find((tab) => tab.id === activeTabId) ?? tabs[0]

  function tabBody() {
    if (selected?.id === 'mapping') {
      return category === 'object_type'
        ? <ObjectMappingTab resource={resource} />
        : <RelationMappingTab resource={resource} />
    }
    if (selected?.id === 'overview') return <RelationOverviewTab resource={resource} />
    if (selected?.id === 'data') return <ObjectDataTab resource={resource} />
    if (selected?.id === 'instances') return <RelationInstancesTab resource={resource} />
    return (
      <RunsTab
        resource={resource}
        run={run}
        isLoading={isRunLoading}
        error={runError}
        onRefresh={onRefreshRun}
      />
    )
  }

  return (
    <section className="ontology-workspace" aria-label={`${resource.name} 工作区`}>
      <header className="ontology-workspace__header">
        <div>
          <span className="eyebrow">{RESOURCE_CATEGORY_LABELS[category]}</span>
          <h1>{resource.name}</h1>
          <p>{resourceDirection(resource)}</p>
        </div>
        <div className="ontology-workspace__meta">
          <span className={`ontology-status is-${resource.status}`}>
            {RESOURCE_STATUS_LABELS[resource.status] ?? resource.status}
          </span>
          {run ? (
            <span className={`ontology-run-status is-${run.status.toLowerCase()}`}>
              {RUN_STATUS_LABELS[run.status] ?? run.status}
            </span>
          ) : null}
        </div>
      </header>
      <ResourceTabBar tabs={tabs} activeId={selected?.id ?? 'mapping'} onChange={onTabChange} />
      <div className="ontology-workspace__body" role="tabpanel">
        {tabBody()}
      </div>
    </section>
  )
}
