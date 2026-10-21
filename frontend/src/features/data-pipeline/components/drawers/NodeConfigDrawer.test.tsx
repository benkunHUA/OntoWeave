import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import type { ConfigSchema, NodeDefinition, PipelineNode } from '../../../../shared/types'
import { WIDGETS } from '../../lib/constants'
import { NodeConfigDrawer } from './NodeConfigDrawer'

const outputDefinition: NodeDefinition = {
  type: 'output',
  version: 1,
  label: '数据输出',
  category: 'output',
  ports: [],
  config_schema: {
    type: 'object',
    properties: { outputId: { type: 'string', format: 'uuid' } },
  },
  ui_schema: { fields: { outputId: { widget: 'select' } } },
}

function definitionFor(
  type: string,
  properties: Record<string, ConfigSchema>,
  fields: Record<string, { widget: string }>,
): NodeDefinition {
  return {
    type,
    version: 1,
    label: type,
    category: 'transform',
    ports: [],
    config_schema: { type: 'object', properties },
    ui_schema: { fields },
  }
}

function render(node: PipelineNode, definition = outputDefinition): string {
  return renderToStaticMarkup(createElement(NodeConfigDrawer, {
    pipelineId: '00000000-0000-4000-8000-000000000010',
    revision: 1,
    node,
    definition,
    issues: [],
    isSaving: false,
    onClose: () => undefined,
    onSave: () => null,
    onDelete: () => null,
    onSetOutput: () => null,
  }))
}

describe('NodeConfigDrawer output binding', () => {
  it('offers set_output controls for an unbound output node', () => {
    const html = render({
      id: '00000000-0000-4000-8000-000000000001',
      type: 'output',
      name: 'Orders',
      config: {},
      position: { x: 0, y: 0 },
    })

    expect(html).toContain('data-editor="output-select"')
    expect(html).toContain('data-editor="expectation-list"')
    expect(html).toContain('保存输出配置')
  })

  it('shows the bound output id as read-only after set_output', () => {
    const html = render({
      id: '00000000-0000-4000-8000-000000000001',
      type: 'output',
      name: 'Orders',
      config: { outputId: '00000000-0000-4000-8000-000000000099' },
      position: { x: 0, y: 0 },
    })

    expect(html).toContain('00000000-0000-4000-8000-000000000099')
    expect(html).toContain('输出已绑定')
  })
})

describe('NodeConfigDrawer uiSchema widgets', () => {
  it('renders the condition tree widget without JSON textarea', () => {
    const html = render({
      id: '00000000-0000-4000-8000-000000000001',
      type: 'filter',
      name: 'Filter',
      config: { condition: { kind: 'compare', field: 'id', op: 'is_not_null' } },
      position: { x: 0, y: 0 },
    }, definitionFor('filter', { condition: { type: 'object' } }, {
      condition: { widget: WIDGETS.conditionTree },
    }))

    expect(html).toContain('data-editor="condition-tree"')
    expect(html).not.toContain('<textarea')
  })

  it('renders the expression widget without JSON textarea', () => {
    const html = render({
      id: '00000000-0000-4000-8000-000000000001',
      type: 'derive_column',
      name: 'Derive',
      config: { outputField: 'amount', expression: { kind: 'field', field: 'amount' } },
      position: { x: 0, y: 0 },
    }, definitionFor('derive_column', {
      outputField: { type: 'string' },
      expression: { type: 'object' },
    }, {
      outputField: { widget: WIDGETS.text },
      expression: { widget: WIDGETS.expressionTree },
    }))

    expect(html).toContain('data-editor="expression-tree"')
    expect(html).not.toContain('<textarea')
  })

  it('renders multi-select widgets as chips instead of comma text', () => {
    const html = render({
      id: '00000000-0000-4000-8000-000000000001',
      type: 'select_columns',
      name: 'Select',
      config: { mode: 'include', columns: ['id'] },
      position: { x: 0, y: 0 },
    }, definitionFor('select_columns', {
      mode: { type: 'string', enum: ['include', 'exclude'] },
      columns: { type: 'array', minItems: 1, items: { type: 'string' } },
    }, {
      mode: { widget: WIDGETS.select },
      columns: { widget: WIDGETS.multiSelect },
    }))

    expect(html).toContain('data-editor="field-multi-select"')
    expect(html).toContain('type="checkbox"')
    expect(html).not.toContain('用逗号分隔')
  })
})
