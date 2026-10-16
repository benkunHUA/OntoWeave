export interface ViewSpec {
  nodes?: Record<string, { x: number; y: number }>
  layout?: {
    kind?: 'hierarchical' | 'force' | 'domain-grouped' | 'grid'
    options?: Record<string, unknown>
  }
  groups?: Array<{ id: string; label: string; nodeIds: string[] }>
  collapsed?: string[]
  highlight?: Array<{ nodeIds: string[]; style: string }>
  dim?: string[]
  filters?: Record<string, unknown>
  annotations?: Array<{ nodeId: string; text: string }>
  [key: string]: unknown
}

export interface ViewState {
  graph_id: string
  name: string | null
  spec: ViewSpec
}
