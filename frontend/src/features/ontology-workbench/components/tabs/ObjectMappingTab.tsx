import { Boxes } from 'lucide-react'
import { TAB_COPY } from '../../lib/constants'
import { TabPlaceholder } from './TabPlaceholder'
import type { ResourceTabProps } from './types'

export function ObjectMappingTab({ resource }: ResourceTabProps) {
  return (
    <TabPlaceholder
      icon={<Boxes size={26} />}
      title={TAB_COPY.objectMapping.title}
      body={`${resource.name}：${TAB_COPY.objectMapping.body}`}
      className="ontology-canvas-placeholder"
    />
  )
}
