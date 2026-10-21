import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import { OutputSelect } from './OutputSelect'

function render(outputId: string | null): string {
  return renderToStaticMarkup(createElement(OutputSelect, {
    nodeId: '00000000-0000-4000-8000-000000000001',
    defaultName: 'Orders',
    outputId,
    isReadOnly: false,
    isSaving: false,
    onSetOutput: () => null,
  }))
}

describe('OutputSelect', () => {
  it('collects name, snapshot mode, primary keys and expectations', () => {
    const html = render(null)

    expect(html).toContain('data-editor="output-select"')
    expect(html).toContain('Orders')
    expect(html).toContain('snapshot')
    expect(html).toContain('data-editor="field-multi-select"')
    expect(html).toContain('data-editor="expectation-list"')
    expect(html).toContain('保存输出配置')
  })

  it('shows the bound output id as read-only state', () => {
    const html = render('00000000-0000-4000-8000-000000000099')

    expect(html).toContain('00000000-0000-4000-8000-000000000099')
    expect(html).toContain('输出已绑定')
    expect(html).not.toContain('<textarea')
  })
})
