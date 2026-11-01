import dagre from '@dagrejs/dagre'
import { useId, useMemo } from 'react'
import {
  EVIDENCE_GRAPH_LAYOUT,
  EVIDENCE_WARNING_COPY,
  QUERY_COPY,
} from '../lib/constants'
import type {
  EvidenceGraph as EvidenceGraphData,
  EvidenceNode,
} from '../lib/evidenceGraph'

const {
  nodeWidth: NODE_WIDTH,
  nodeHeight: NODE_HEIGHT,
  margin: GRAPH_MARGIN,
  labelCharWidth: LABEL_CHAR_WIDTH,
  labelHeight: LABEL_HEIGHT,
  minWidth: MIN_GRAPH_WIDTH,
  minHeight: MIN_GRAPH_HEIGHT,
} = EVIDENCE_GRAPH_LAYOUT

interface LayoutNode extends EvidenceNode {
  x: number
  y: number
  typeName: string | null
}

interface LayoutEdge {
  id: string
  source: LayoutNode
  target: LayoutNode
  label: string
}

interface GraphLayout {
  nodes: LayoutNode[]
  edges: LayoutEdge[]
  width: number
  height: number
}

interface EvidenceGraphProps {
  graph: EvidenceGraphData
}

/** 实例证据图：节点是查询命中的对象实例，边完全继承业务图关系定义。 */
export function EvidenceGraph({ graph }: EvidenceGraphProps) {
  const markerId = useId().replaceAll(':', '')
  const layout = useMemo(() => layoutGraph(graph), [graph])

  return (
    <section className="query-evidence-graph">
      <div className="query-evidence-graph__caption">
        <strong>{graph.graphName || QUERY_COPY.evidenceGraphTitle}</strong>
        <span>
          {graph.nodes.length} {QUERY_COPY.evidenceGraphObjects} · {graph.edges.length}{' '}
          {QUERY_COPY.evidenceGraphRelations}
        </span>
      </div>
      <div className="query-evidence-graph__viewport">
        <svg
          className="query-evidence-graph__svg"
          width={layout.width}
          height={layout.height}
          viewBox={`0 0 ${layout.width} ${layout.height}`}
          role="img"
          aria-label={QUERY_COPY.evidenceGraphLabel}
        >
          <defs>
            <marker
              id={markerId}
              viewBox="0 0 10 10"
              refX="8"
              refY="5"
              markerWidth="6"
              markerHeight="6"
              markerUnits="userSpaceOnUse"
              orient="auto"
            >
              <path className="query-evidence-graph__arrow" d="M 0 0 L 10 5 L 0 10 z" />
            </marker>
          </defs>
          {layout.edges.map((edge) => (
            <EdgePath key={edge.id} edge={edge} markerId={markerId} />
          ))}
          {layout.nodes.map((node) => (
            <GraphNode key={node.id} node={node} />
          ))}
        </svg>
      </div>
      <GraphNotes graph={graph} />
    </section>
  )
}

function GraphNode({ node }: { node: LayoutNode }) {
  const title = nodeTitle(node)
  const meta = nodeMeta(node)
  return (
    <g className="query-evidence-graph__node" transform={`translate(${node.x} ${node.y})`}>
      <title>{`${title}${meta ? ` · ${meta}` : ''}`}</title>
      <rect width={NODE_WIDTH} height={NODE_HEIGHT} rx={10} />
      <text className="query-evidence-graph__node-title" x={10} y={19}>
        {truncate(title, 15)}
      </text>
      {meta ? (
        <text className="query-evidence-graph__node-meta" x={10} y={35}>
          {truncate(meta, 22)}
        </text>
      ) : null}
    </g>
  )
}

function EdgePath({ edge, markerId }: { edge: LayoutEdge; markerId: string }) {
  const start = boundaryPoint(edge.source, edge.target)
  const end = boundaryPoint(edge.target, edge.source)
  const labelX = (start.x + end.x) / 2
  const labelY = (start.y + end.y) / 2
  const labelWidth = Math.max(24, edge.label.length * LABEL_CHAR_WIDTH + 8)

  return (
    <g className="query-evidence-graph__edge-group">
      <path
        className="query-evidence-graph__edge"
        d={`M ${start.x} ${start.y} L ${end.x} ${end.y}`}
        markerEnd={`url(#${markerId})`}
      />
      <g transform={`translate(${labelX - labelWidth / 2} ${labelY - LABEL_HEIGHT / 2})`}>
        <rect
          className="query-evidence-graph__edge-label-bg"
          width={labelWidth}
          height={LABEL_HEIGHT}
          rx={4}
        />
        <text
          className="query-evidence-graph__edge-label"
          x={labelWidth / 2}
          y={10.5}
          textAnchor="middle"
        >
          {truncate(edge.label, 12)}
        </text>
      </g>
    </g>
  )
}

function GraphNotes({ graph }: { graph: EvidenceGraphData }) {
  const notes = graph.warnings.flatMap((warning) => {
    const copy = EVIDENCE_WARNING_COPY[warning]
    return copy ? [copy] : []
  })
  if (!graph.edges.length) notes.push(QUERY_COPY.evidenceGraphNoEdges)
  if (!notes.length) return null
  return <p className="query-evidence-graph__note">{notes.join(' · ')}</p>
}

function layoutGraph(graph: EvidenceGraphData): GraphLayout {
  const typeNames = new Map(graph.objectTypes.map((item) => [item.id, item.name]))
  const dagreGraph = new dagre.graphlib.Graph()
  dagreGraph.setDefaultEdgeLabel(() => ({}))
  dagreGraph.setGraph({
    rankdir: graph.nodes.length > 4 ? 'TB' : 'LR',
    nodesep: 22,
    ranksep: 52,
    marginx: GRAPH_MARGIN,
    marginy: GRAPH_MARGIN,
  })

  for (const node of graph.nodes) {
    dagreGraph.setNode(node.id, { width: NODE_WIDTH, height: NODE_HEIGHT })
  }
  for (const edge of graph.edges) {
    dagreGraph.setEdge(edge.source, edge.target)
  }
  dagre.layout(dagreGraph)

  const nodes = graph.nodes.map<LayoutNode>((node) => {
    const point = dagreGraph.node(node.id) as { x: number; y: number }
    return {
      ...node,
      x: point.x - NODE_WIDTH / 2,
      y: point.y - NODE_HEIGHT / 2,
      typeName: typeNames.get(node.objectTypeId) ?? null,
    }
  })
  const byId = new Map(nodes.map((node) => [node.id, node]))
  const edges = graph.edges.flatMap<LayoutEdge>((edge) => {
    const source = byId.get(edge.source)
    const target = byId.get(edge.target)
    return source && target ? [{ id: edge.id, source, target, label: edge.label }] : []
  })
  const maxX = Math.max(...nodes.map((node) => node.x + NODE_WIDTH), 0)
  const maxY = Math.max(...nodes.map((node) => node.y + NODE_HEIGHT), 0)

  return {
    nodes,
    edges,
    width: Math.max(maxX + GRAPH_MARGIN, MIN_GRAPH_WIDTH),
    height: Math.max(maxY + GRAPH_MARGIN, MIN_GRAPH_HEIGHT),
  }
}

function boundaryPoint(from: LayoutNode, toward: LayoutNode): { x: number; y: number } {
  const fromCenter = { x: from.x + NODE_WIDTH / 2, y: from.y + NODE_HEIGHT / 2 }
  const towardCenter = { x: toward.x + NODE_WIDTH / 2, y: toward.y + NODE_HEIGHT / 2 }
  const dx = towardCenter.x - fromCenter.x
  const dy = towardCenter.y - fromCenter.y
  const scale = Math.min(
    Math.abs(dx) ? (NODE_WIDTH / 2 + 4) / Math.abs(dx) : Number.POSITIVE_INFINITY,
    Math.abs(dy) ? (NODE_HEIGHT / 2 + 4) / Math.abs(dy) : Number.POSITIVE_INFINITY,
  )
  return { x: fromCenter.x + dx * scale, y: fromCenter.y + dy * scale }
}

function nodeTitle(node: LayoutNode): string {
  return cleanText(node.title) ?? node.typeName ?? node.primaryKey
}

function nodeMeta(node: LayoutNode): string | null {
  const title = nodeTitle(node)
  const parts = [node.typeName, node.primaryKey].filter(
    (part): part is string => Boolean(part) && part !== title,
  )
  return parts.length ? parts.join(' · ') : null
}

function cleanText(value: string | null): string | null {
  const text = value?.trim()
  return text || null
}

function truncate(value: string, maxLength: number): string {
  return value.length > maxLength ? `${value.slice(0, maxLength - 1)}…` : value
}
