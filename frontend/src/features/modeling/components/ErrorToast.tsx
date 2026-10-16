import { CircleAlert, X } from 'lucide-react'
import { useModeling } from '../context/modelingContext'

/** 对话栏右下角的错误提示：取数、保存、运行失败都从这里出。 */
export function ErrorToast() {
  const { toast } = useModeling()
  if (!toast.error) return null

  return (
    <div className="error-toast">
      <CircleAlert size={16} />
      <span>{toast.error}</span>
      <button type="button" aria-label="关闭错误" onClick={toast.clear}>
        <X size={14} />
      </button>
    </div>
  )
}
