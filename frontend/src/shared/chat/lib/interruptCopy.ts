/** 确认卡片的提示文案与输入框占位符，集中放这里，组件只负责渲染。 */

export const INTERRUPT_HINT = {
  freeText: '也可以直接在下方输入回答',
  optionsOnly: '请点击上面的选项回答',
} as const

export const INTERRUPT_PLACEHOLDER = {
  freeText: '回答上面的问题',
  optionsOnly: '请点击上面的选项回答',
} as const
