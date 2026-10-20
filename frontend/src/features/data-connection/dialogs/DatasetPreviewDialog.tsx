import { Maximize2, X } from 'lucide-react'
import type { DatasetPreview } from '../../../shared/types'
import { DialogShell } from '../../modeling/components/dialogs/DialogShell'
import { DatasetPreviewTable } from '../components/context/DatasetPreviewTable'

interface DatasetPreviewDialogProps {
  title: string
  preview: DatasetPreview | null
  loading: boolean
  error: string | null
  onClose: () => void
}

export function DatasetPreviewDialog({
  title,
  preview,
  loading,
  error,
  onClose,
}: DatasetPreviewDialogProps) {
  return (
    <DialogShell
      className="view-dialog--wide"
      icon={<Maximize2 size={20} />}
      title="数据预览"
      subtitle={title}
      onClose={onClose}
      footer={
        <button type="button" className="secondary-button" onClick={onClose}>
          <X size={15} />
          关闭
        </button>
      }
    >
      <div className="view-dialog__body dataset-preview-dialog">
        {preview ? (
          <p className="dataset-preview-dialog__summary">
            共 {preview.row_count.toLocaleString()} 行，当前显示前 {Math.min(preview.rows.length, 100)} 行
          </p>
        ) : null}
        <DatasetPreviewTable preview={preview} loading={loading} error={error} />
      </div>
    </DialogShell>
  )
}
