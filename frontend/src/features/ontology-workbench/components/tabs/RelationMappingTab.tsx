import { Share2 } from 'lucide-react'
import { TAB_COPY } from '../../lib/constants'
import { TabPlaceholder } from './TabPlaceholder'
import type { ResourceTabProps } from './types'

export function RelationMappingTab({ resource }: ResourceTabProps) {
  return (
    <TabPlaceholder
      icon={<Share2 size={26} />}
      title={TAB_COPY.relationMapping.title}
      body={`${resource.name}：${TAB_COPY.relationMapping.body}`}
      className="ontology-canvas-placeholder"
    />
  )
}
