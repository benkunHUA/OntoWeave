import { History } from 'lucide-react'
import { RUN_STATUS_LABELS, TAB_COPY } from '../../lib/constants'
import { TabPlaceholder } from './TabPlaceholder'
import type { RunsTabProps } from './types'

export function RunsTab({ resource, run, isLoading, error, onRefresh }: RunsTabProps) {
  const status = run ? RUN_STATUS_LABELS[run.status] ?? run.status : null
  const body = error
    ? error
    : run
      ? `${resource.name} 最近一次运行：${status}`
      : `${resource.name}${TAB_COPY.runs.body}`
  return (
    <TabPlaceholder
      icon={<History size={26} />}
      title={TAB_COPY.runs.title}
      body={body}
      className="ontology-data-placeholder"
      aside={
        <button type="button" className="secondary-button" onClick={onRefresh} disabled={isLoading}>
          {isLoading ? '读取中' : '刷新运行'}
        </button>
      }
    />
  )
}
