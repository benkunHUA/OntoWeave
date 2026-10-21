import type { NodeCatalog, PipelineDraftResponse } from '../../../shared/types'

export const INPUT_ID = '30000000-0000-4000-8000-000000000001'
export const FILTER_A = '30000000-0000-4000-8000-000000000002'
export const FILTER_B = '30000000-0000-4000-8000-000000000006'
export const OUTPUT_ID = '30000000-0000-4000-8000-000000000003'

export const catalog: NodeCatalog = {
  catalog_version: 'catalog-v1',
  nodes: [
    {
      type: 'input',
      version: 1,
      label: '输入',
      category: 'source',
      ports: [
        { id: 'out', role: 'output', data_type: 'table', required: true, multiple: false },
      ],
      config_schema: { type: 'object', properties: {} },
      ui_schema: { fields: {} },
    },
    {
      type: 'filter',
      version: 1,
      label: '筛选',
      category: 'transform',
      ports: [
        { id: 'in', role: 'input', data_type: 'table', required: true, multiple: false },
        { id: 'out', role: 'output', data_type: 'table', required: true, multiple: false },
      ],
      config_schema: { type: 'object', properties: {} },
      ui_schema: { fields: {} },
    },
    {
      type: 'union',
      version: 1,
      label: '合并',
      category: 'transform',
      ports: [
        { id: 'inputs', role: 'input', data_type: 'table', required: true, multiple: true },
        { id: 'out', role: 'output', data_type: 'table', required: true, multiple: false },
      ],
      config_schema: { type: 'object', properties: {} },
      ui_schema: { fields: {} },
    },
    {
      type: 'output',
      version: 1,
      label: '输出',
      category: 'output',
      ports: [
        { id: 'in', role: 'input', data_type: 'table', required: true, multiple: false },
      ],
      config_schema: { type: 'object', properties: {} },
      ui_schema: { fields: {} },
    },
  ],
}

export function draftFixture(): PipelineDraftResponse {
  return {
    pipeline_id: '30000000-0000-4000-8000-0000000000ff',
    revision: 3,
    definition: {
      schemaVersion: 1,
      nodes: [
        {
          id: INPUT_ID,
          type: 'input',
          name: '订单',
          config: { datasetKind: 'sync', datasetId: 'dataset-1' },
          position: { x: 80, y: 180 },
        },
        {
          id: FILTER_A,
          type: 'filter',
          name: '有效订单',
          config: { condition: { kind: 'compare', field: 'id', op: 'gt' } },
          position: { x: 320, y: 180 },
        },
        {
          id: OUTPUT_ID,
          type: 'output',
          name: '订单输出',
          config: { outputId: 'output-1' },
          position: { x: 560, y: 180 },
        },
      ],
      edges: [
        {
          id: '30000000-0000-4000-8000-000000000004',
          sourceNodeId: INPUT_ID,
          sourcePort: 'out',
          targetNodeId: FILTER_A,
          targetPort: 'in',
        },
        {
          id: '30000000-0000-4000-8000-000000000005',
          sourceNodeId: FILTER_A,
          sourcePort: 'out',
          targetNodeId: OUTPUT_ID,
          targetPort: 'in',
        },
      ],
    },
  }
}
