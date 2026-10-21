import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import { WIDGETS } from '../../../lib/constants'
import { ConfigField } from './ConfigField'
import { rowsConfig } from './rowsConfig'

function render(widget: string, field = 'value', value: unknown = undefined): string {
  return renderToStaticMarkup(createElement(ConfigField, {
    field,
    widget,
    schema: { type: 'object' },
    value,
    onChange: () => undefined,
  }))
}

describe('ConfigField dedicated editors', () => {
  it('renders the condition tree editor without a JSON textarea', () => {
    const html = render(WIDGETS.conditionTree, 'condition', {
      kind: 'compare',
      field: 'status',
      op: 'eq',
      value: { kind: 'literal', dataType: 'string', value: 'paid' },
    })

    expect(html).toContain('data-editor="condition-tree"')
    expect(html).not.toContain('<textarea')
  })

  it('renders the expression editor without a JSON textarea', () => {
    const html = render(WIDGETS.expressionTree, 'expression', {
      kind: 'field',
      field: 'amount',
    })

    expect(html).toContain('data-editor="expression-tree"')
    expect(html).not.toContain('<textarea')
  })

  it('renders field multi-select as chips and checkboxes', () => {
    const html = render(WIDGETS.fieldSelector, 'columns', ['id'])

    expect(html).toContain('data-editor="field-multi-select"')
    expect(html).toContain('type="checkbox"')
    expect(html).toContain('id')
    expect(html).not.toContain('用逗号分隔')
  })

  it('routes aggregate groupBy through the dedicated picker', () => {
    const html = render(WIDGETS.fieldSelector, 'groupBy', ['status'])

    expect(html).toContain('data-editor="group-by-picker"')
    expect(html).toContain('type="checkbox"')
  })
})

describe('aggregate empty row', () => {
  it('preselects the first schema enum value for op', () => {
    const rows = rowsConfig(WIDGETS.aggregationList, {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          op: { type: 'string', enum: ['count', 'sum', 'avg'] },
          outputField: { type: 'string' },
        },
      },
    })

    expect(rows?.emptyRow.op).toBe('count')
  })
})
