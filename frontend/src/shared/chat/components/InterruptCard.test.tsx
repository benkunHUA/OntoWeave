// @vitest-environment jsdom

import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen } from '../../../test/render'
import type { ActionConfirmation, MessageBlock } from '../../types'
import { InterruptCard } from './InterruptCard'

type InterruptBlock = Extract<MessageBlock, { kind: 'interrupt' }>

const confirmation: ActionConfirmation = {
  kind: 'action_confirmation',
  title: '执行「关闭工单」？',
  summary: '修改 1 个对象',
  action: { id: 'action-1', name: '关闭工单' },
  preview_hash: 'hash',
  execution_id: 'execution-1',
  object_changes: [],
  relation_changes: [],
  warnings: [],
  options: [{ label: '确认执行', answer: { decision: 'approve' } }],
}

afterEach(cleanup)

describe('InterruptCard', () => {
  it('keeps rendering plain ask_user interrupts as before', () => {
    const block: InterruptBlock = {
      id: 'i1',
      kind: 'interrupt',
      question: '客户还是供应商？',
      options: [
        { label: '客户', answer: '客户' },
        { label: '供应商', answer: '供应商' },
      ],
      freeText: { wrap: 'raw' },
      details: [],
    }

    render(<InterruptCard block={block} pending disabled={false} onAnswer={() => {}} />)

    expect(screen.getByText('客户还是供应商？')).toBeTruthy()
    expect(screen.getByRole('button', { name: '客户' })).toBeTruthy()
  })

  it('switches to the rich card when a structured confirmation is present', () => {
    const block: InterruptBlock = {
      id: 'i2',
      kind: 'interrupt',
      question: confirmation.title,
      options: confirmation.options,
      freeText: null,
      details: [],
      confirmation,
    }

    const { container } = render(
      <InterruptCard block={block} pending disabled={false} onAnswer={() => {}} />,
    )

    expect(container.querySelector('.action-confirmation')).toBeTruthy()
    expect(screen.getByText('修改 1 个对象')).toBeTruthy()
  })
})
