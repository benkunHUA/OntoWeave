import { Boxes } from 'lucide-react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useErrorToast } from '../../../shared/chat/hooks/useErrorToast'
import type { OntologyResource } from '../../../shared/types'
import { WorkbenchPaneLayout } from '../../../shared/workbench/WorkbenchPaneLayout'
import { useWorkbenchPaneWidth } from '../../../shared/workbench/useWorkbenchPaneWidth'
import { OntologyConversationPane } from '../components/OntologyConversationPane'
import { ResourceSidebar } from '../components/ResourceSidebar'
import { ResourceWorkspace } from '../components/ResourceWorkspace'
import { useIndexRun } from '../hooks/useIndexRun'
import { useOntologyConversation } from '../hooks/useOntologyConversation'
import { useOntologyResources } from '../hooks/useOntologyResources'
import {
  CONVERSATION_PANE_STORAGE_KEY,
  CONVERSATION_PANE_WIDTH,
  ONTOLOGY_PATH,
  RESOURCE_COPY,
  RESOURCE_QUERY_KEY,
  RESOURCE_SIDEBAR_WIDTH,
  type ResourceTabId,
} from '../lib/constants'
import {
  formatResourceRef,
  parseResourceRef,
  resourceKey,
  resourceTabs,
} from '../lib/format'

export function OntologyWorkbenchView() {
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const toast = useErrorToast()
  const resourceList = useOntologyResources()
  const layout = useWorkbenchPaneWidth({
    storageKey: CONVERSATION_PANE_STORAGE_KEY,
    defaultWidth: CONVERSATION_PANE_WIDTH.default,
    min: CONVERSATION_PANE_WIDTH.min,
    max: CONVERSATION_PANE_WIDTH.max,
    cssVariable: '--workbench-center-w',
    resizerSide: 'right',
  })

  const allResources = useMemo(
    () => [
      ...(resourceList.resources?.object_types ?? []),
      ...(resourceList.resources?.foreign_key_relations ?? []),
      ...(resourceList.resources?.many_to_many_relations ?? []),
    ],
    [resourceList.resources],
  )
  const reference = useMemo(
    () => parseResourceRef(searchParams.get(RESOURCE_QUERY_KEY)),
    [searchParams],
  )
  const selected = useMemo(
    () => allResources.find((resource) => resource.kind === reference?.kind && resource.id === reference.id) ?? null,
    [allResources, reference],
  )
  const selectedKey = resourceKey(selected)
  const tabs = selected ? resourceTabs(selected) : []
  const defaultTabId = tabs[0]?.id ?? null
  const [activeTab, setActiveTab] = useState<{ resourceKey: string; tabId: ResourceTabId } | null>(null)
  const activeTabId = activeTab?.resourceKey === selectedKey ? activeTab.tabId : defaultTabId
  const changeTab = useCallback(
    (tabId: ResourceTabId) => setActiveTab({ resourceKey: selectedKey, tabId }),
    [selectedKey],
  )

  const selectResource = useCallback(
    (resource: OntologyResource, replace = false) => {
      setSearchParams((current) => {
        const next = new URLSearchParams(current)
        next.set(RESOURCE_QUERY_KEY, formatResourceRef(resource))
        return next
      }, { replace })
    },
    [setSearchParams],
  )

  useEffect(() => {
    if (resourceList.isLoading || selected || !allResources.length) return
    selectResource(allResources[0], true)
  }, [allResources, resourceList.isLoading, selectResource, selected])

  const indexRun = useIndexRun(selected)
  const refreshResources = resourceList.refresh
  const refreshRun = indexRun.refresh
  const handleConversationFinished = useCallback(() => {
    void refreshResources()
    void refreshRun()
  }, [refreshResources, refreshRun])
  const conversation = useOntologyConversation({
    resource: selected,
    report: toast.report,
    setMessage: toast.setMessage,
    clearMessage: toast.clear,
    onFinished: handleConversationFinished,
  })

  return (
    <WorkbenchPaneLayout
      pageClassName="ontology-workbench-page"
      dividerClassName="ontology-workbench-divider"
      leftWidth={RESOURCE_SIDEBAR_WIDTH}
      centerWidth={layout.width}
      dragging={layout.dragging}
      resizerLabel="拖拽调整对象映射对话栏宽度"
      onStartResize={layout.startResize}
      left={
        <ResourceSidebar
          resources={resourceList.resources}
          activeKey={selectedKey}
          isLoading={resourceList.isLoading}
          error={resourceList.error}
          onRetry={() => void resourceList.refresh()}
          onSelect={(resource) => selectResource(resource)}
          onHome={() => navigate('/')}
        />
      }
      center={
        <OntologyConversationPane
          resource={selected}
          messages={conversation.messages}
          events={conversation.events}
          isRunning={conversation.agent.isRunning}
          isLoading={conversation.isLoading}
          interrupt={conversation.agent.interrupt}
          error={toast.error}
          onClearError={toast.clear}
          onSend={conversation.send}
          onAnswer={conversation.answer}
          onAbort={conversation.agent.abort}
        />
      }
      right={
        selected ? (
          <ResourceWorkspace
            resource={selected}
            tabs={tabs}
            activeTabId={activeTabId ?? 'mapping'}
            onTabChange={changeTab}
            run={indexRun.run}
            isRunLoading={indexRun.isLoading}
            runError={indexRun.error}
            onRefreshRun={() => void indexRun.refresh()}
          />
        ) : (
          <section className="ontology-workspace ontology-workspace--empty">
            <span className="ontology-tab-panel__icon"><Boxes size={28} /></span>
            <h1>{RESOURCE_COPY.emptyTitle}</h1>
            <p>{RESOURCE_COPY.emptyBody}</p>
            <button type="button" className="secondary-button" onClick={() => navigate(ONTOLOGY_PATH)}>
              返回工作台
            </button>
          </section>
        )
      }
    />
  )
}
