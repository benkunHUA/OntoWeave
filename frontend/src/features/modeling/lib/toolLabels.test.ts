import { describe, expect, it } from 'vitest'
import { subagentLabel, toolLabel } from './toolLabels'

describe('toolLabel', () => {
  it('names the subagent a task was delegated to', () => {
    expect(toolLabel('task', { subagent_type: 'action_modeler' })).toBe('委派 · 动作建模')
    expect(toolLabel('task', { subagent_type: 'view_designer' })).toBe('委派 · 视觉整理')
  })

  it('falls back for an unknown subagent or missing args', () => {
    expect(toolLabel('task')).toBe('委派子智能体')
    expect(toolLabel('task', { subagent_type: 'newcomer' })).toBe('委派 · newcomer')
  })

  it('labels the action tools and the merged search tool', () => {
    expect(toolLabel('search')).toBe('检索已有元素')
    expect(toolLabel('propose_action')).toBe('新建动作')
    expect(toolLabel('set_action_criteria')).toBe('设置提交校验')
    expect(toolLabel('unknown_tool')).toBe('正在执行 unknown_tool')
  })

  it('spells out known subagents only', () => {
    expect(subagentLabel('action_modeler')).toBe('动作建模')
    expect(subagentLabel('mystery')).toBe('mystery')
  })
})
