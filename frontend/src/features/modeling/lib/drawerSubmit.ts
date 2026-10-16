/** 抽屉保存的两种落法：只登记草稿，或登记后立刻确认。 */
export interface DrawerSubmitOptions {
  /** true = 保存后立刻确认这批改动，不用再去左侧确认条点一次。 */
  confirm: boolean
}
