import type {
  ConfigSchema,
  JsonObject,
  NodeCatalog,
  NodeDefinition,
  PipelineNode,
  PipelineNodePort,
} from '../../../shared/types'
import { CATEGORY_LABELS, CATEGORY_ORDER } from './constants'

export function findNodeDefinition(
  catalog: NodeCatalog | null,
  type: string,
): NodeDefinition | null {
  return catalog?.nodes.find((node) => node.type === type) ?? null
}

export function groupedNodeDefinitions(
  catalog: NodeCatalog | null,
): Array<{ category: string; label: string; nodes: NodeDefinition[] }> {
  if (!catalog) return []
  return CATEGORY_ORDER.map((category) => ({
    category,
    label: CATEGORY_LABELS[category] ?? category,
    nodes: catalog.nodes.filter((node) => node.category === category),
  })).filter((group) => group.nodes.length > 0)
}

export function inputPorts(definition: NodeDefinition | null): PipelineNodePort[] {
  return definition?.ports.filter((port) => port.role === 'input') ?? []
}

export function outputPorts(definition: NodeDefinition | null): PipelineNodePort[] {
  return definition?.ports.filter((port) => port.role === 'output') ?? []
}

function defaultValue(schema: ConfigSchema | undefined): unknown {
  if (!schema) return undefined
  if (Array.isArray(schema.enum) && schema.enum.length) return schema.enum[0]
  if (schema.type === 'array') return []
  if (schema.type === 'boolean') return false
  if (schema.type === 'object') return undefined
  return undefined
}

export function defaultNodeConfig(definition: NodeDefinition): JsonObject {
  const config: JsonObject = {}
  for (const [field, schema] of Object.entries(definition.config_schema.properties ?? {})) {
    const value = defaultValue(schema)
    if (value !== undefined) config[field] = value
  }
  return config
}

export function uniqueNodeName(
  definition: NodeDefinition,
  nodes: PipelineNode[],
): string {
  const used = new Set(nodes.map((node) => node.name))
  if (!used.has(definition.label)) return definition.label
  let index = 2
  while (used.has(`${definition.label} ${index}`)) index += 1
  return `${definition.label} ${index}`
}

export function columnNames(schema: unknown): string[] {
  if (!schema || typeof schema !== 'object') return []
  const root = schema as Record<string, unknown>
  const columns = Array.isArray(root.columns) ? root.columns : Array.isArray(root.fields) ? root.fields : []
  return columns.flatMap((column) => {
    if (!column || typeof column !== 'object') return []
    const name = (column as Record<string, unknown>).name
    return typeof name === 'string' ? [name] : []
  })
}
