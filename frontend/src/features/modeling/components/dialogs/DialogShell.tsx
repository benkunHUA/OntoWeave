import { X } from 'lucide-react'
import type { ReactNode } from 'react'

export type DialogIconTone = 'default' | 'danger'

interface DialogShellProps {
  icon: ReactNode
  iconTone?: DialogIconTone
  title: string
  subtitle: string
  className?: string
  /** 右上角关闭按钮与遮罩层共用的收起动作。 */
  onClose: () => void
  /** 底部按钮区，由调用方决定按钮语义。 */
  footer: ReactNode
  children: ReactNode
}

/**
 * 弹窗外壳：遮罩、标题栏（图标 + 标题 + 副标题 + 关闭）和底部栏。
 * 内容区留给调用方，省得每个弹窗各写一遍重复的骨架。
 */
export function DialogShell({
  icon,
  iconTone = 'default',
  title,
  subtitle,
  className,
  onClose,
  footer,
  children,
}: DialogShellProps) {
  const iconClassName =
    iconTone === 'danger' ? 'view-dialog__icon view-dialog__icon--danger' : 'view-dialog__icon'

  return (
    <div className="dialog-backdrop" role="presentation">
      <section className={`view-dialog${className ? ` ${className}` : ''}`} role="dialog" aria-modal="true">
        <header>
          <div>
            <span className={iconClassName}>{icon}</span>
            <div>
              <strong>{title}</strong>
              <span className="view-dialog__subtitle">{subtitle}</span>
            </div>
          </div>
          <button
            type="button"
            className="icon-button icon-button--quiet"
            aria-label="关闭"
            onClick={onClose}
          >
            <X size={16} />
          </button>
        </header>
        {children}
        <footer>{footer}</footer>
      </section>
    </div>
  )
}
