import { ActionIcon, Button } from '@mantine/core'
import { CircleCheck, Save, X } from 'lucide-react'
import type { ReactNode } from 'react'
import type { Status } from '../../../../shared/types'
import type { DrawerSubmitOptions } from '../../lib/drawerSubmit'
import { STATUS_LABEL } from '../../lib/status'

interface DrawerShellProps {
  ariaLabel: string
  icon: ReactNode
  title: string
  status: Status
  /** 额外的样式类：抽屉宽度等按元素类型微调。 */
  className?: string
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
  className,
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
    <aside className={`drawer${className ? ` ${className}` : ''}`} aria-label={ariaLabel}>
      <header className="drawer__header">
        <span className="drawer__icon">{icon}</span>
        <div className="drawer__heading">
          <strong>{title}</strong>
          <span className={`status-dot status-dot--${status}`}>{STATUS_LABEL[status]}</span>
        </div>
        <ActionIcon
          type="button"
          variant="subtle"
          color="gray"
          aria-label="关闭详情"
          onClick={onClose}
        >
          <X size={16} />
        </ActionIcon>
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
          <Button type="button" variant="default" onClick={onClose}>
            关闭
          </Button>
          <Button
            type="submit"
            variant="default"
            disabled={!canSave}
            leftSection={<Save size={15} />}
          >
            保存
          </Button>
          <Button
            type="button"
            disabled={!canSave}
            loading={isSaving}
            leftSection={isSaving ? undefined : <CircleCheck size={15} />}
            onClick={() => onSubmit({ confirm: true })}
          >
            {isSaving ? '处理中' : '保存并确认'}
          </Button>
        </footer>
      </form>
    </aside>
  )
}
