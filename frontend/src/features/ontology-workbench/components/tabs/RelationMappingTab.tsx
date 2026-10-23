import { MappingCanvas } from '../canvas/MappingCanvas'
import type { ResourceTabProps } from './types'

export function RelationMappingTab({ resource }: ResourceTabProps) {
  return <MappingCanvas mode="relation" resource={resource} />
}
