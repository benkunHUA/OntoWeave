import { CircleCheck, LoaderCircle, Save, X } from 'lucide-react'
import type { ReactNode } from 'react'
import type { Status } from '../../../../shared/types'
import { STATUS_LABEL } from '../../lib/status'

export interface DrawerSubmitOptions {
  /** true = 保存后立刻确认这批改动，不用再去左侧确认条点一次。 */
  confirm: boolean
}

interface DrawerShellProps {
  ariaLabel: string
  icon: ReactNode
  title: string
  status: Status
  isSaving: boolean
  canSave: boolean
  error: string | null
  hint: string | null
  onClose: () => void
  onSubmit: (options: DrawerSubmitOptions) => void
  /** 额外的次要动作，比如「删除关系」，排在左下角。 */
  extraActions?: ReactNode
  children: ReactNode
}

/** 抽屉的共享外壳：头部（图标/标题/状态/关闭）+ 表单 + 底部动作条。 */
export function DrawerShell({
  ariaLabel,
  icon,
  title,
  status,
  isSaving,
  canSave,
  error,
  hint,
  onClose,
  onSubmit,
  extraActions,
  children,
}: DrawerShellProps) {
  return (
    <aside className="drawer" aria-label={ariaLabel}>
      <header className="drawer__header">
        <span className="drawer__icon">{icon}</span>
        <div className="drawer__heading">
          <strong>{title}</strong>
          <span className={`status-dot status-dot--${status}`}>{STATUS_LABEL[status]}</span>
        </div>
        <button
          type="button"
          className="icon-button icon-button--quiet"
          aria-label="关闭详情"
          onClick={onClose}
        >
          <X size={16} />
        </button>
      </header>

      <form
        className="drawer__form"
        onSubmit={(event) => {
          event.preventDefault()
          if (canSave) onSubmit({ confirm: false })
        }}
      >
        {children}

        {error ? <p className="drawer__error">{error}</p> : null}
        {hint ? <p className="drawer__hint">{hint}</p> : null}

        <footer className="drawer__footer">
          {extraActions}
          <button type="button" className="secondary-button" onClick={onClose}>
            关闭
          </button>
          <button type="submit" className="secondary-button" disabled={!canSave}>
            <Save size={15} />
            保存
          </button>
          <button
            type="button"
            className="primary-button"
            disabled={!canSave}
            onClick={() => onSubmit({ confirm: true })}
          >
            {isSaving ? <LoaderCircle size={15} /> : <CircleCheck size={15} />}
            {isSaving ? '处理中' : '保存并确认'}
          </button>
        </footer>
      </form>
    </aside>
  )
}
