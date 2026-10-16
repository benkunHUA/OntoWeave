import { Plus } from 'lucide-react'
import type { ReactNode } from 'react'

interface ActionSectionProps {
  title: string
  count: number
  hint: string
  actionLabel: string
  onAdd: () => void
  emptyCopy: string
  empty: boolean
  children: ReactNode
}

/** 抽屉里的一块（参数 / 规则 / 提交校验）：标题、计数、说明、新增按钮与内容。 */
export function ActionSection({
  title,
  count,
  hint,
  actionLabel,
  onAdd,
  emptyCopy,
  empty,
  children,
}: ActionSectionProps) {
  return (
    <section className="action-section">
      <div className="action-section__head">
        <h4>
          {title} <em>{count}</em>
        </h4>
        <button type="button" className="text-button" onClick={onAdd}>
          <Plus size={14} />
          {actionLabel}
        </button>
      </div>
      <p className="action-section__hint">{hint}</p>
      {empty ? <p className="action-section__empty">{emptyCopy}</p> : children}
    </section>
  )
}
