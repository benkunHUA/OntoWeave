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

/** 兼容旧版 Java 目录里大写端口角色，浏览器契约统一按小写消费。 */
export function portHasRole(port: PipelineNodePort, role: PipelineNodePort['role']): boolean {
  return String(port.role).toLowerCase() === role
}

export function inputPorts(definition: NodeDefinition | null): PipelineNodePort[] {
  return definition?.ports.filter((port) => portHasRole(port, 'input')) ?? []
}

export function outputPorts(definition: NodeDefinition | null): PipelineNodePort[] {
  return definition?.ports.filter((port) => portHasRole(port, 'output')) ?? []
}

export function nodeCategory(
  definition: NodeDefinition | null,
  nodeType: string,
): string {
  if (definition?.category) return definition.category
  if (nodeType === 'input') return 'source'
  if (nodeType === 'output') return 'output'
  return 'transform'
}

function defaultValue(schema: ConfigSchema | undefined): unknown {
  if (!schema) return undefined
  if (schema.default !== undefined) return schema.default
  if (Array.isArray(schema.enum) && schema.enum.length) return schema.enum[0]
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
