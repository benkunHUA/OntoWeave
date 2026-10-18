import { ErrorToast as ChatErrorToast } from '../../../shared/chat/components/ErrorToast'
import { useModeling } from '../context/modelingContext'

/** 建模对话栏的错误提示：消息来自共享 toast 状态。 */
export function ErrorToast() {
  const { toast } = useModeling()
  return <ChatErrorToast message={toast.error} onClose={toast.clear} />
}
