import { describe, expect, it } from 'vitest'
import type { ConfigSchema, NodeCatalog, NodeDefinition } from '../../../shared/types'
import { defaultNodeConfig, inputPorts, outputPorts } from './nodeCatalog'

function definition(type: string, properties: Record<string, ConfigSchema>): NodeDefinition {
  return {
    type,
    version: 1,
    label: type,
    category: type === 'input' ? 'source' : type === 'output' ? 'output' : 'transform',
    ports: [],
    config_schema: { type: 'object', properties },
    ui_schema: { fields: {} },
  }
}

/** 与 Java PipelineNodeCatalog 的 10 类节点及 config schema 关键约束对齐。 */
const realCatalog: NodeCatalog = {
  catalog_version: 'catalog-v1',
  nodes: [
    definition('input', {
      datasetKind: { type: 'string', enum: ['sync'] },
      datasetId: { type: 'string', format: 'uuid' },
    }),
    definition('filter', {
      condition: { type: 'object' },
    }),
    definition('select_columns', {
      mode: { type: 'string', enum: ['include', 'exclude'] },
      columns: { type: 'array', minItems: 1, items: { type: 'string' } },
    }),
    definition('rename_columns', {
      renames: {
        type: 'array',
        items: {
          type: 'object',
          required: ['from', 'to'],
          properties: { from: { type: 'string' }, to: { type: 'string' } },
        },
      },
    }),
    definition('derive_column', {
      outputField: { type: 'string' },
      expression: { type: 'object' },
    }),
    definition('deduplicate', {
      mode: { type: 'string', enum: ['all', 'subset'] },
      columns: { type: 'array', items: { type: 'string' } },
    }),
    definition('join', {
      joinType: { type: 'string', enum: ['left', 'right', 'inner', 'full'] },
      keys: {
        type: 'array',
        minItems: 1,
        items: {
          type: 'object',
          required: ['left', 'right'],
          properties: { left: { type: 'string' }, right: { type: 'string' } },
        },
      },
    }),
    definition('union', {}),
    definition('aggregate', {
      groupBy: { type: 'array', items: { type: 'string' } },
      aggregations: {
        type: 'array',
        minItems: 1,
        items: {
          type: 'object',
          required: ['op', 'outputField'],
          properties: {
            op: { type: 'string', enum: ['count', 'sum', 'min', 'max', 'avg'] },
            field: { type: 'string' },
            outputField: { type: 'string' },
          },
        },
      },
    }),
    definition('output', {
      outputId: { type: 'string', format: 'uuid' },
    }),
  ],
}

describe('defaultNodeConfig', () => {
  it('covers the real 10-node catalog shape', () => {
    expect(realCatalog.nodes).toHaveLength(10)
  })

  it('never emits empty arrays for minItems>=1 fields', () => {
    for (const node of realCatalog.nodes) {
      const config = defaultNodeConfig(node)
      for (const [field, schema] of Object.entries(node.config_schema.properties ?? {})) {
        if (schema.type !== 'array') continue
        expect(config[field], `${node.type}.${field} 不应猜测空数组`).toBeUndefined()
      }
    }
  })

  it('uses only explicit defaults and safe enum first values', () => {
    for (const node of realCatalog.nodes) {
      const config = defaultNodeConfig(node)
      for (const [field, schema] of Object.entries(node.config_schema.properties ?? {})) {
        if (schema.default !== undefined) {
          expect(config[field]).toEqual(schema.default)
          continue
        }
        if (Array.isArray(schema.enum) && schema.enum.length) {
          expect(config[field]).toBe(schema.enum[0])
          continue
        }
        expect(config[field], `${node.type}.${field} 不应被猜测`).toBeUndefined()
      }
    }
  })
})

describe('port roles', () => {
  it('accepts uppercase roles from older data-service versions', () => {
    const node = definition('input', {})
    node.ports = [
      { id: 'out', role: 'OUTPUT' as 'output', data_type: 'table', required: true, multiple: false },
      { id: 'in', role: 'INPUT' as 'input', data_type: 'table', required: true, multiple: false },
    ]

    expect(inputPorts(node).map((port) => port.id)).toEqual(['in'])
    expect(outputPorts(node).map((port) => port.id)).toEqual(['out'])
  })
})
