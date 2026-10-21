/** 新回合生成 turn id；中断续跑沿用当前值，避免把一轮拆成两条。 */
export function resolveTurnId(current: string | null, resuming: boolean): string {
  return resuming && current ? current : crypto.randomUUID()
}
