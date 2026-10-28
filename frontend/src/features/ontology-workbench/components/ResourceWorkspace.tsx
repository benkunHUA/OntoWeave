import { Badge, Group, Stack, Text, Title } from '@mantine/core'
import { Database } from 'lucide-react'
import type { IndexRun, OntologyResource } from '../../../shared/types'
import {
  RESOURCE_CATEGORY_LABELS,
  RESOURCE_STATUS_LABELS,
  RUN_STATUS_LABELS,
  type ResourceTabId,
} from '../lib/constants'
import { bindingLabel, resourceCategory, resourceDirection } from '../lib/format'
import { ResourceCategoryIcon } from './ResourceCategoryIcon'
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
  const direction = resourceDirection(resource)
  const binding = bindingLabel(resource.binding)
  const isBound = Boolean(
    resource.binding?.binding_id
      || resource.binding?.id
      || resource.binding?.draft_dataset_id
      || resource.binding?.active_dataset_id
      || resource.binding?.current_index_version_id,
  )

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
        <Group className="ontology-workspace__identity" gap="sm" wrap="nowrap">
          <span className="ontology-workspace__icon">
            <ResourceCategoryIcon category={category} size={17} />
          </span>
          <Stack className="ontology-workspace__copy" gap={2} align="flex-start">
            <Text className="eyebrow" size="xs" c="dimmed">{RESOURCE_CATEGORY_LABELS[category]}</Text>
            <Group className="ontology-workspace__title-row" gap="sm" align="baseline">
              <Title order={1}>{resource.name}</Title>
              {category === 'object_type' ? null : (
                <Badge color="blue" variant="light">{direction}</Badge>
              )}
            </Group>
          </Stack>
        </Group>
        <ResourceTabBar tabs={tabs} activeId={selected?.id ?? 'mapping'} onChange={onTabChange} />
        <Group className="ontology-workspace__meta" gap={6}>
          <Badge
            color={isBound ? 'teal' : 'gray'}
            variant="light"
            leftSection={<Database size={11} />}
          >
            {binding}
          </Badge>
          <Badge color="blue" variant="light">
            {RESOURCE_STATUS_LABELS[resource.status] ?? resource.status}
          </Badge>
          {run ? (
            <Badge color={run.status === 'SUCCEEDED' ? 'teal' : run.status === 'FAILED' ? 'red' : 'gray'} variant="light">
              {RUN_STATUS_LABELS[run.status] ?? run.status}
            </Badge>
          ) : null}
        </Group>
      </header>
      <div className="ontology-workspace__body" role="tabpanel">
        {tabBody()}
      </div>
    </section>
  )
}
