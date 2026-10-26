import type { ActionBrief } from './action'
import type { Status } from './common'

export interface GraphProperty {
  id: string
  name: string
  name_en?: string | null
  datatype: string
  required: boolean
  is_array: boolean
  is_primary_key: boolean
  is_title_key: boolean
  /** datatype = enum 时的候选值；其余类型为 null。 */
  enum_values: string[] | null
  status: Status
}

export interface GraphNode {
  id: string
  name: string
  name_en?: string | null
  description: string | null
  status: Status
  properties: GraphProperty[]
}

export interface GraphRelation {
  id: string
  name: string
  source_type_id: string
  target_type_id: string
  direction: string
  cardinality: string
  forward_label: string | null
  reverse_label: string | null
  /** 一对一 / 一对多：承载这条链接的外键属性 id（存对端主键）；多对多为 null。 */
  foreign_key_property_id: string | null
  status: Status
}

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
