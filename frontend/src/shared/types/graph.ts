import type { ActionBrief } from './action'
import type { GraphNode, GraphRelation } from './ontology'

export interface GraphSnapshot {
  graph: {
    id: string
    name: string | null
  }
  nodes: GraphNode[]
  relations: GraphRelation[]
  actions: ActionBrief[]
}

/** 删会话 / 删图时，对共享池里对象与关系的处理范围。 */
export type DeleteScope =
  | 'graph-only'
  | 'graph-and-drafts'
  | 'graph-and-unreferenced'
