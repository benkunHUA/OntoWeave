// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '../../../test/render'
import type { ActionConfirmation } from '../../types'
import { ActionConfirmationCard } from './ActionConfirmationCard'

const confirmation: ActionConfirmation = {
  kind: 'action_confirmation',
  title: '执行「关闭工单」？',
  summary: '修改 1 个对象；新增 1 条关系',
  action: { id: 'action-1', name: '关闭工单', description: '关闭处理中的工单' },
  preview_hash: 'hash',
  execution_id: 'execution-1',
  object_changes: [
    {
      kind: 'OBJECT_MODIFY',
      object_type_id: 'type-1',
      object_type_name: '工单',
      primary_key: 'T-1',
      title: '打印机故障',
      fields: [
        { property_id: 'p1', name: '解决说明', before: null, after: '已修复' },
        { property_id: 'p2', name: '状态', before: '处理中', after: '已关闭' },
      ],
    },
    {
      kind: 'OBJECT_CREATE',
      object_type_id: 'type-2',
      object_type_name: '回访记录',
      primary_key: 'R-1',
      title: '回访记录 R-1',
      fields: [],
    },
  ],
  relation_changes: [
    {
      kind: 'RELATION_CREATE',
      relation_id: 'relation-1',
      relation_name: '使用配件',
      from_object_type_id: 'type-1',
      from_object_type_name: '工单',
      from_primary_key: 'T-1',
      to_object_type_id: 'type-3',
      to_object_type_name: '配件',
      to_primary_key: 'P-1',
      before_exists: false,
      after_exists: true,
    },
  ],
  warnings: ['该工单还有未完成的子任务'],
  options: [
    { label: '确认执行', answer: { decision: 'approve' } },
    { label: '取消', answer: { decision: 'reject' } },
  ],
}

afterEach(cleanup)

describe('ActionConfirmationCard', () => {
  it('shows the action summary, object fields and relation endpoints', () => {
    render(
      <ActionConfirmationCard
        confirmation={confirmation}
        pending
        disabled={false}
        onAnswer={() => {}}
      />,
    )

    expect(screen.getByText('执行「关闭工单」？')).toBeTruthy()
    expect(screen.getByText('修改 1 个对象；新增 1 条关系')).toBeTruthy()
    expect(screen.getByText('修改对象')).toBeTruthy()
    expect(screen.getByText('新增对象')).toBeTruthy()
    expect(screen.getByText('打印机故障')).toBeTruthy()
    expect(screen.getByText('解决说明')).toBeTruthy()
    expect(screen.getByText('已修复')).toBeTruthy()
    expect(screen.getByText('使用配件')).toBeTruthy()
    expect(screen.getByText('配件 P-1')).toBeTruthy()
    expect(screen.getByText('该工单还有未完成的子任务')).toBeTruthy()
  })

  it('submits the confirmation answers carried by each option', () => {
    const onAnswer = vi.fn()
    render(
      <ActionConfirmationCard
        confirmation={confirmation}
        pending
        disabled={false}
        onAnswer={onAnswer}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: '确认执行' }))
    fireEvent.click(screen.getByRole('button', { name: '取消' }))

    expect(onAnswer.mock.calls.map(([option]) => option.answer)).toEqual([
      { decision: 'approve' },
      { decision: 'reject' },
    ])
  })

  it('disables the decision buttons while applying', () => {
    render(
      <ActionConfirmationCard
        confirmation={confirmation}
        pending
        disabled
        onAnswer={() => {}}
      />,
    )

    expect(screen.getByRole('button', { name: '确认执行' })).toHaveProperty('disabled', true)
    expect(screen.getByRole('button', { name: '取消' })).toHaveProperty('disabled', true)
    expect(screen.getByText('正在提交，请稍候。')).toBeTruthy()
  })
})
