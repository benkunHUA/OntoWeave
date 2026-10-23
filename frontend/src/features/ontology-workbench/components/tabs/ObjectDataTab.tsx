import { Database } from 'lucide-react'
import { TAB_COPY } from '../../lib/constants'
import { TabPlaceholder } from './TabPlaceholder'
import type { ResourceTabProps } from './types'

export function ObjectDataTab({ resource }: ResourceTabProps) {
  return (
    <TabPlaceholder
      icon={<Database size={26} />}
      title={TAB_COPY.objectData.title}
      body={`${resource.name}：${TAB_COPY.objectData.body}`}
      className="ontology-data-placeholder"
    />
  )
}
