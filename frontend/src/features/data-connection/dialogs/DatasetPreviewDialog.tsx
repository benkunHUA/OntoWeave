import { Button, Stack, Text } from '@mantine/core'
import { Maximize2, X } from 'lucide-react'
import type { DatasetPreview } from '../../../shared/types'
import { DatasetPreviewTable } from '../../../shared/components/DatasetPreviewTable'
import { DialogShell } from '../../../shared/components/dialogs/DialogShell'
import { DATASET_PREVIEW_LIMIT } from '../lib/constants'

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
      icon={<Maximize2 size={20} />}
      title="数据预览"
      subtitle={title}
      size="xl"
      onClose={onClose}
      footer={
        <Button type="button" variant="default" leftSection={<X size={15} />} onClick={onClose}>
          关闭
        </Button>
      }
    >
      <Stack className="view-dialog__body dataset-preview-dialog" gap="xs">
        {preview ? (
          <Text className="dataset-preview-dialog__summary" size="xs" c="dimmed">
            共 {preview.row_count.toLocaleString()} 行，当前显示前 {Math.min(preview.rows.length, 100)} 行
          </Text>
        ) : null}
        <DatasetPreviewTable
          preview={preview}
          loading={loading}
          error={error}
          limit={DATASET_PREVIEW_LIMIT}
        />
      </Stack>
    </DialogShell>
  )
}
