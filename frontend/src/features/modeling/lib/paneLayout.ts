/** 三个面板的宽度配置与本地持久化。尺寸属于配置，不写在组件里。 */

export const PANE_WIDTHS_STORAGE_KEY = 'ontoweave.pane-widths'

export const DEFAULT_PANE_WIDTHS = { conversations: 268, chat: 400 }

export const PANE_MIN_WIDTHS = { conversations: 200, chat: 320 }

export const PANE_MAX_WIDTHS = { conversations: 460, chat: 760 }

export type PaneKey = keyof typeof DEFAULT_PANE_WIDTHS
export type PaneWidths = typeof DEFAULT_PANE_WIDTHS

export function clampPaneWidth(key: PaneKey, value: number): number {
  return Math.min(Math.max(value, PANE_MIN_WIDTHS[key]), PANE_MAX_WIDTHS[key])
}

/** 读本地存的宽度；读不到或坏了就回默认，绝不因此让页面挂掉。 */
export function readPaneWidths(): PaneWidths {
  try {
    const raw = window.localStorage.getItem(PANE_WIDTHS_STORAGE_KEY)
    if (!raw) return DEFAULT_PANE_WIDTHS
    const parsed = JSON.parse(raw) as Partial<Record<PaneKey, number>>
    return {
      conversations: clampPaneWidth(
        'conversations',
        Number(parsed.conversations) || DEFAULT_PANE_WIDTHS.conversations,
      ),
      chat: clampPaneWidth('chat', Number(parsed.chat) || DEFAULT_PANE_WIDTHS.chat),
    }
  } catch {
    return DEFAULT_PANE_WIDTHS
  }
}

export function writePaneWidths(widths: PaneWidths): void {
  try {
    window.localStorage.setItem(PANE_WIDTHS_STORAGE_KEY, JSON.stringify(widths))
  } catch {
    // 隐私模式等场景下 localStorage 不可用，忽略。
  }
}
