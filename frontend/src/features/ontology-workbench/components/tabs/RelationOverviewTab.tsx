import { KeyRound } from 'lucide-react'
import { TAB_COPY } from '../../lib/constants'
import { resourceDirection } from '../../lib/format'
import { TabPlaceholder } from './TabPlaceholder'
import type { ResourceTabProps } from './types'

export function RelationOverviewTab({ resource }: ResourceTabProps) {
  return (
    <TabPlaceholder
      icon={<KeyRound size={26} />}
      title={TAB_COPY.relationOverview.title}
      body={`${resourceDirection(resource)}：${TAB_COPY.relationOverview.body}`}
      className="ontology-canvas-placeholder"
    />
  )
}
