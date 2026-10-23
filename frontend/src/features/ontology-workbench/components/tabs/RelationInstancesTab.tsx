import { ListTree } from 'lucide-react'
import { TAB_COPY } from '../../lib/constants'
import { TabPlaceholder } from './TabPlaceholder'
import type { ResourceTabProps } from './types'

export function RelationInstancesTab({ resource }: ResourceTabProps) {
  return (
    <TabPlaceholder
      icon={<ListTree size={26} />}
      title={TAB_COPY.relationInstances.title}
      body={`${resource.name}：${TAB_COPY.relationInstances.body}`}
      className="ontology-data-placeholder"
    />
  )
}
