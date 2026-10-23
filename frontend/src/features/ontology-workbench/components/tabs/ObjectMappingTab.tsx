import { MappingCanvas } from '../canvas/MappingCanvas'
import type { ResourceTabProps } from './types'

export function ObjectMappingTab({ resource }: ResourceTabProps) {
  return <MappingCanvas mode="object" resource={resource} />
}
