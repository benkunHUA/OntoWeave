import { CircleAlert, X } from 'lucide-react'

interface ErrorToastProps {
  message: string | null
  onClose: () => void
}

/** 页面右下角的错误提示：取数、保存、运行失败都从这里出。 */
export function ErrorToast({ message, onClose }: ErrorToastProps) {
  if (!message) return null

  return (
    <div className="error-toast">
      <CircleAlert size={16} />
      <span>{message}</span>
      <button type="button" aria-label="关闭错误" onClick={onClose}>
        <X size={14} />
      </button>
    </div>
  )
}
