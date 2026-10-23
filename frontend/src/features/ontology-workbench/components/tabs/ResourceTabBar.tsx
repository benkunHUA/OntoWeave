import type { ResourceTabDefinition, ResourceTabId } from '../../lib/constants'

interface ResourceTabBarProps {
  tabs: readonly ResourceTabDefinition[]
  activeId: ResourceTabId
  onChange: (tabId: ResourceTabId) => void
}

export function ResourceTabBar({ tabs, activeId, onChange }: ResourceTabBarProps) {
  return (
    <nav className="ontology-tabs" role="tablist" aria-label="对象工作台视图">
      {tabs.map((tab) => (
        <button
          key={tab.id}
          type="button"
          role="tab"
          aria-selected={tab.id === activeId}
          className={`ontology-tabs__tab ${tab.id === activeId ? 'is-active' : ''}`}
          onClick={() => onChange(tab.id)}
        >
          {tab.label}
        </button>
      ))}
    </nav>
  )
}
