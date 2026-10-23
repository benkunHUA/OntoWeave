import type { Node } from '@xyflow/react'

export interface MappingField {
  id: string
  label: string
  typeLabel: string
  meta: string | null
  badges: string[]
  mapped: boolean
  blocking: boolean
  warning: boolean
  disabled?: boolean
}

export interface MappingKeyOption {
  id: string
  label: string
}

export interface MappingNodeData extends Record<string, unknown> {
  side: 'left' | 'right'
  title: string
  subtitle: string
  fields: MappingField[]
  keyControls?: {
    primaryValue: string
    titleValue: string | null
    primaryOptions: MappingKeyOption[]
    titleOptions: MappingKeyOption[]
    onPrimaryChange: (value: string) => void
    onTitleChange: (value: string | null) => void
  }
}

export type MappingFlowNode = Node<MappingNodeData, 'mapping'>
