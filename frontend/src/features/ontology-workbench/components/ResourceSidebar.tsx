import {
  ActionIcon,
  Badge,
  Button,
  Group,
  ScrollArea,
  Stack,
  Text,
  UnstyledButton,
} from '@mantine/core'
import { House, Network, RefreshCw } from 'lucide-react'
import { useMemo, useState } from 'react'
import { Brand } from '../../../shared/components/Brand'
import { EmptyState } from '../../../shared/components/EmptyState'
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
      <Group className="ontology-sidebar__header" justify="space-between" wrap="nowrap">
        <Brand
          compact
          icon={<Network size={17} />}
          title="OntoWeave"
          subtitle={RESOURCE_COPY.appLabel}
        />
        <ActionIcon type="button" variant="subtle" color="gray" aria-label="返回首页" onClick={onHome}>
          <House size={17} />
        </ActionIcon>
      </Group>

      <SearchField
        className="ontology-sidebar__search"
        value={query}
        onChange={(event) => setQuery(event.target.value)}
        placeholder={RESOURCE_COPY.searchPlaceholder}
        aria-label={RESOURCE_COPY.searchPlaceholder}
      />

      <ScrollArea className="ontology-resource-groups" type="auto" offsetScrollbars>
        <Stack component="nav" aria-label="对象与关系" gap="md" p="xs">
          {groups.map((group) => (
            <Stack component="section" className="ontology-resource-group" key={group.category} gap={4}>
              <Group gap={6} px="xs">
                <ResourceCategoryIcon category={group.category} />
                <Text component="h2" size="xs" fw={700} c="dimmed">{group.label}</Text>
                <Badge size="xs" color="gray" variant="light" ml="auto">{group.items.length}</Badge>
              </Group>
              {group.items.map((resource) => {
                const active = `${resource.kind}:${resource.id}` === activeKey
                return (
                  <UnstyledButton
                    type="button"
                    key={`${resource.kind}:${resource.id}`}
                    className={`ontology-resource ${active ? 'is-active' : ''}`}
                    aria-current={active ? 'page' : undefined}
                    onClick={() => onSelect(resource)}
                  >
                    <Stack className="ontology-resource__body" gap={2} align="flex-start">
                      <Text component="strong" size="sm">{resource.name}</Text>
                      <Text component="span" size="xs" c="dimmed">{resourceDirection(resource)}</Text>
                    </Stack>
                    <Badge size="xs" color="gray" variant="light">
                      {bindingLabel(resource.binding)}
                    </Badge>
                  </UnstyledButton>
                )
              })}
            </Stack>
          ))}
          {isLoading && !allResources.length ? (
            <EmptyState className="ontology-sidebar__empty" title={RESOURCE_COPY.loading} />
          ) : null}
          {!isLoading && !allResources.length ? (
            <EmptyState
              className="ontology-sidebar__empty"
              title={RESOURCE_COPY.emptyTitle}
              body={RESOURCE_COPY.emptyBody}
            />
          ) : null}
          {!isLoading && allResources.length && !groups.some((group) => group.items.length) ? (
            <EmptyState className="ontology-sidebar__empty" title={RESOURCE_COPY.emptySearch} />
          ) : null}
          {error ? (
            <Stack className="ontology-sidebar__error" gap="xs" p="xs">
              <Text size="xs" c="red">{error}</Text>
              <Button
                type="button"
                variant="default"
                size="xs"
                leftSection={<RefreshCw size={13} />}
                onClick={onRetry}
              >
                {RESOURCE_COPY.retry}
              </Button>
            </Stack>
          ) : null}
        </Stack>
      </ScrollArea>
    </aside>
  )
}
