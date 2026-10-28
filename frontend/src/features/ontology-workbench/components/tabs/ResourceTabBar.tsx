import { Tabs } from '@mantine/core'
import type { ResourceTabDefinition, ResourceTabId } from '../../lib/constants'

interface ResourceTabBarProps {
  tabs: readonly ResourceTabDefinition[]
  activeId: ResourceTabId
  onChange: (tabId: ResourceTabId) => void
}

export function ResourceTabBar({ tabs, activeId, onChange }: ResourceTabBarProps) {
  return (
    <Tabs
      className="ontology-tabs"
      value={activeId}
      onChange={(value) => {
        if (value) onChange(value as ResourceTabId)
      }}
      aria-label="对象工作台视图"
    >
      <Tabs.List>
        {tabs.map((tab) => (
          <Tabs.Tab key={tab.id} value={tab.id}>
            {tab.label}
          </Tabs.Tab>
        ))}
      </Tabs.List>
    </Tabs>
  )
}
