import dagre from '@dagrejs/dagre'
import { MarkerType, type Edge, type Node, type XYPosition } from '@xyflow/react'
import type { Changeset, GraphSnapshot, ViewSpec } from '../../../shared/types'
import { ACTION_NODE_HEIGHT, ACTION_NODE_WIDTH } from './actionConfig'
import { cardinalityMark, relationEdgeLabel } from './relationDetails'

export interface OntologyNodeData extends Record<string, unknown> {
  label: string
  description: string | null
  status: 'draft' | 'confirmed' | 'approved'
  properties: number
  draft: boolean
  ghosted: boolean
  highlighted: boolean
  dimmed: boolean
  groupLabel: string | null
  annotation: string | null
}

export type OntologyFlowNode = Node<OntologyNodeData, 'ontology'>

export interface ActionNodeData extends Record<string, unknown> {
  label: string
  description: string | null
  status: 'draft' | 'confirmed' | 'approved'
  draft: boolean
  ghosted: boolean
  highlighted: boolean
  dimmed: boolean
}

export type ActionFlowNode = Node<ActionNodeData, 'action'>

export type FlowNode = OntologyFlowNode | ActionFlowNode

const NODE_WIDTH = 224
const NODE_HEIGHT = 116
const POSITION_GAP_X = 48
const POSITION_GAP_Y = 48
/** 动作节点单独排一行，挂在对象图下方，不掺进对象之间的布局。 */
const ACTION_GAP_X = 40
const ACTION_GAP_Y = 96

function positionsOverlap(left: XYPosition, right: XYPosition) {
  return (
    Math.abs(left.x - right.x) < NODE_WIDTH + POSITION_GAP_X &&
    Math.abs(left.y - right.y) < NODE_HEIGHT + POSITION_GAP_Y
  )
}

function findFreePosition(
  candidate: XYPosition,
  occupied: XYPosition[],
): XYPosition {
  if (!occupied.some((position) => positionsOverlap(candidate, position))) {
    return candidate
  }

  const stepX = NODE_WIDTH + POSITION_GAP_X
  const stepY = NODE_HEIGHT + POSITION_GAP_Y
  for (let radius = 1; radius <= 30; radius += 1) {
    const offsets: Array<[number, number]> = []
    for (let dx = -radius; dx <= radius; dx += 1) {
      offsets.push([dx, -radius], [dx, radius])
    }
    for (let dy = -radius + 1; dy < radius; dy += 1) {
      offsets.push([-radius, dy], [radius, dy])
    }
    for (const [dx, dy] of offsets) {
      const trial = {
        x: candidate.x + dx * stepX,
        y: candidate.y + dy * stepY,
      }
      if (!occupied.some((position) => positionsOverlap(trial, position))) {
        return trial
      }
    }
  }

  return candidate
}

function resolvePositions(
  ids: string[],
  baseLayout: Record<string, XYPosition>,
  saved: Record<string, XYPosition> | undefined,
): Record<string, XYPosition> {
  const resolved: Record<string, XYPosition> = {}
  const occupied: XYPosition[] = []

  // 用户手工摆放过的坐标优先保留；自动布局只负责没有坐标的节点。
  for (const id of ids) {
    const position = saved?.[id]
    if (!position) continue
    resolved[id] = position
    occupied.push(position)
  }

  for (const id of ids) {
    if (resolved[id]) continue
    const position = findFreePosition(
      baseLayout[id] ?? { x: 0, y: 0 },
      occupied,
    )
    resolved[id] = position
    occupied.push(position)
  }

  return resolved
}

/** 动作排成一行落在对象图下方，每个动作横向居中于它涉及的对象；互相重叠时依次右移。 */
function layoutActionRow(
  snapshot: GraphSnapshot,
  positions: Record<string, XYPosition>,
): Record<string, XYPosition> {
  if (!snapshot.actions.length) return {}
  const bottoms = Object.values(positions).map((position) => position.y + NODE_HEIGHT)
  const rowTop = (bottoms.length ? Math.max(...bottoms) : 0) + ACTION_GAP_Y

  const preferred = snapshot.actions.map((action, index) => {
    const anchors = action.involves
      .map((target) => positions[target.id])
      .filter((position): position is XYPosition => Boolean(position))
    if (!anchors.length) return { id: action.id, index, x: 0 }
    const left = Math.min(...anchors.map((position) => position.x))
    const right = Math.max(...anchors.map((position) => position.x + NODE_WIDTH))
    return { id: action.id, index, x: (left + right) / 2 - ACTION_NODE_WIDTH / 2 }
  })

  // 按期望位置排序后再铺开：动作与它涉及的对象对得上，线不用横跨整行。
  const ordered = [...preferred].sort((left, right) => left.x - right.x || left.index - right.index)
  const row: Record<string, XYPosition> = {}
  let cursor: number | null = null
  for (const item of ordered) {
    const x: number =
      cursor === null ? item.x : Math.max(item.x, cursor + ACTION_NODE_WIDTH + ACTION_GAP_X)
    row[item.id] = { x, y: rowTop }
    cursor = x
  }
  return row
}

/** `involves` 虚线从离目标近的那一侧出去，两根线不至于挤在同一个端口上。 */
function involvesSide(
  source: XYPosition,
  target: XYPosition,
): 'left' | 'right' {
  const sourceCenter = source.x + ACTION_NODE_WIDTH / 2
  const targetCenter = target.x + NODE_WIDTH / 2
  return targetCenter < sourceCenter ? 'left' : 'right'
}

function layoutHierarchical(snapshot: GraphSnapshot): Record<string, XYPosition> {
  const graph = new dagre.graphlib.Graph()
  graph.setDefaultEdgeLabel(() => ({}))
  graph.setGraph({ rankdir: 'LR', nodesep: 58, ranksep: 104, marginx: 40, marginy: 40 })
  snapshot.nodes.forEach((node) => {
    graph.setNode(node.id, { width: NODE_WIDTH, height: NODE_HEIGHT })
  })
  snapshot.relations.forEach((relation) => {
    graph.setEdge(relation.source_type_id, relation.target_type_id)
  })
  dagre.layout(graph)
  return Object.fromEntries(
    snapshot.nodes.map((node) => {
      const point = graph.node(node.id)
      return [node.id, { x: point.x - NODE_WIDTH / 2, y: point.y - NODE_HEIGHT / 2 }]
    }),
  )
}

function layoutGrid(snapshot: GraphSnapshot): Record<string, XYPosition> {
  const columns = Math.max(1, Math.ceil(Math.sqrt(snapshot.nodes.length)))
  return Object.fromEntries(
    snapshot.nodes.map((node, index) => [
      node.id,
      {
        x: (index % columns) * (NODE_WIDTH + 72),
        y: Math.floor(index / columns) * (NODE_HEIGHT + 72),
      },
    ]),
  )
}

function layoutForce(snapshot: GraphSnapshot): Record<string, XYPosition> {
  const count = Math.max(snapshot.nodes.length, 1)
  const radius = Math.max(240, count * 46)
  return Object.fromEntries(
    snapshot.nodes.map((node, index) => {
      const angle = (Math.PI * 2 * index) / count - Math.PI / 2
      return [
        node.id,
        {
          x: Math.cos(angle) * radius,
          y: Math.sin(angle) * radius * 0.62,
        },
      ]
    }),
  )
}

function layoutDomainGrouped(
  snapshot: GraphSnapshot,
  spec: ViewSpec,
): Record<string, XYPosition> {
  const groups = spec.groups ?? []
  const groupIndex = new Map(groups.map((group, index) => [group.id, index]))
  const nodeGroup = new Map<string, string>()
  groups.forEach((group) => {
    group.nodeIds.forEach((nodeId) => nodeGroup.set(nodeId, group.id))
  })
  const ordered = [...snapshot.nodes].sort((left, right) => {
    const leftGroup = groupIndex.get(nodeGroup.get(left.id) ?? '') ?? 999
    const rightGroup = groupIndex.get(nodeGroup.get(right.id) ?? '') ?? 999
    return leftGroup - rightGroup || left.name.localeCompare(right.name)
  })
  const columns = Math.max(1, Math.ceil(Math.sqrt(ordered.length)))
  return Object.fromEntries(
    ordered.map((node, index) => [
      node.id,
      {
        x: (index % columns) * (NODE_WIDTH + 90),
        y: Math.floor(index / columns) * (NODE_HEIGHT + 76),
      },
    ]),
  )
}

export function buildFlowElements(
  snapshot: GraphSnapshot | null,
  spec: ViewSpec,
  changeset: Changeset | null,
): { nodes: FlowNode[]; edges: Edge[] } {
  if (!snapshot) return { nodes: [], edges: [] }

  const kind = spec.layout?.kind ?? 'hierarchical'
  const layout =
    kind === 'grid'
      ? layoutGrid(snapshot)
      : kind === 'force'
        ? layoutForce(snapshot)
        : kind === 'domain-grouped'
          ? layoutDomainGrouped(snapshot, spec)
          : layoutHierarchical(snapshot)
  const nodeIds = snapshot.nodes.map((node) => node.id)
  const positions = resolvePositions(nodeIds, layout, spec.nodes)
  const actionPositions = resolvePositions(
    snapshot.actions.map((action) => action.id),
    layoutActionRow(snapshot, positions),
    spec.nodes,
  )

  const deleted = new Set(
    changeset?.ops
      .filter((op) => op.op === 'delete')
      .map((op) => op.target_id)
      .filter((value): value is string => Boolean(value)) ?? [],
  )
  const collapsedGroups = new Set(spec.collapsed ?? [])
  const hiddenNodes = new Set<string>()
  for (const group of spec.groups ?? []) {
    if (!collapsedGroups.has(group.id)) continue
    group.nodeIds.forEach((nodeId) => hiddenNodes.add(nodeId))
  }

  const highlighted = new Set(
    (spec.highlight ?? []).flatMap((entry) => entry.nodeIds),
  )
  const dimmed = new Set(spec.dim ?? [])
  const annotations = new Map(
    (spec.annotations ?? []).map((entry) => [entry.nodeId, entry.text]),
  )
  const groupLabels = new Map<string, string>()
  for (const group of spec.groups ?? []) {
    group.nodeIds.forEach((nodeId) => groupLabels.set(nodeId, group.label))
  }

  const objectNodes: OntologyFlowNode[] = snapshot.nodes
    .filter((node) => !hiddenNodes.has(node.id))
    .map((node) => ({
      id: node.id,
      type: 'ontology',
      position: positions[node.id] ?? { x: 0, y: 0 },
      data: {
        label: node.name,
        description: node.description,
        status: node.status,
        properties: node.properties.length,
        draft: node.status === 'draft' && !deleted.has(node.id),
        ghosted: deleted.has(node.id),
        highlighted: highlighted.has(node.id),
        dimmed: dimmed.has(node.id),
        groupLabel: groupLabels.get(node.id) ?? null,
        annotation: annotations.get(node.id) ?? null,
      },
      style: {
        width: NODE_WIDTH,
        height: NODE_HEIGHT,
      },
    }))

  const actionNodes: ActionFlowNode[] = snapshot.actions.map((action) => ({
    id: action.id,
    type: 'action',
    position: actionPositions[action.id] ?? { x: 0, y: 0 },
    data: {
      label: action.name,
      description: action.description,
      status: action.status,
      draft: action.status === 'draft' && !deleted.has(action.id),
      ghosted: deleted.has(action.id),
      highlighted: highlighted.has(action.id),
      dimmed: dimmed.has(action.id),
    },
    style: {
      width: ACTION_NODE_WIDTH,
      height: ACTION_NODE_HEIGHT,
    },
  }))

  const nodes: FlowNode[] = [...objectNodes, ...actionNodes]
  const visibleNodeIds = new Set(objectNodes.map((node) => node.id))
  const edges: Edge[] = snapshot.relations
    .filter(
      (relation) =>
        visibleNodeIds.has(relation.source_type_id) &&
        visibleNodeIds.has(relation.target_type_id),
    )
    .map((relation) => {
      const ghosted = deleted.has(relation.id)
      const draft = relation.status === 'draft' && !ghosted
      return {
        id: relation.id,
        source: relation.source_type_id,
        target: relation.target_type_id,
        label: `${relationEdgeLabel(relation)} · ${cardinalityMark(relation.cardinality)}`,
        type: 'default',
        markerEnd: { type: MarkerType.ArrowClosed },
        animated: draft,
        className: [
          draft ? 'edge-draft' : '',
          ghosted ? 'edge-ghosted' : '',
        ]
          .filter(Boolean)
          .join(' '),
        style: {
          stroke: ghosted ? '#4a4a4a' : draft ? '#e8a33d' : '#4c8dff',
          strokeWidth: ghosted ? 1.5 : 2,
          strokeDasharray: draft ? '7 5' : undefined,
          opacity: ghosted ? 0.35 : 1,
        },
        labelStyle: {
          fill: '#c8c8c8',
          fontSize: 12,
          fontWeight: 600,
        },
      }
    })

  // 动作与本体元素之间的连线：跟着定义自动生成，删不掉也点不开，
  // 视觉上弱化成虚线，好和用户自己画的关系区分开。
  const involvesEdges: Edge[] = snapshot.actions.flatMap((action) => {
    const ghosted = deleted.has(action.id)
    const origin = actionPositions[action.id] ?? { x: 0, y: 0 }
    return action.involves
      .filter((target) => visibleNodeIds.has(target.id))
      .map((target) => ({
        id: `involves:${action.id}:${target.kind}:${target.id}`,
        source: action.id,
        target: target.id,
        sourceHandle: involvesSide(origin, positions[target.id] ?? origin),
        type: 'default',
        className: 'edge-involves',
        data: { involves: true },
        style: {
          stroke: '#6f5f8f',
          strokeWidth: 1.2,
          strokeDasharray: '2 6',
          opacity: ghosted ? 0.2 : 0.45,
        },
      }))
  })

  return { nodes, edges: [...edges, ...involvesEdges] }
}
