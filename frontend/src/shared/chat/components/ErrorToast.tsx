import { Affix, Alert } from '@mantine/core'
import { CircleAlert } from 'lucide-react'

interface ErrorToastProps {
  message: string | null
  onClose: () => void
}

/** 页面右下角的错误提示：取数、保存、运行失败都从这里出。 */
export function ErrorToast({ message, onClose }: ErrorToastProps) {
  if (!message) return null

  return (
    <Affix position={{ bottom: 24, right: 24 }} zIndex={300}>
      <Alert
        className="error-toast"
        icon={<CircleAlert size={16} />}
        color="red"
        variant="light"
        withCloseButton
        closeButtonLabel="关闭错误"
        onClose={onClose}
        maw={420}
      >
        {message}
      </Alert>
    </Affix>
  )
}
