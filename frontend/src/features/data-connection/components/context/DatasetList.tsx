import { Box, Group, Stack, Text, ThemeIcon, UnstyledButton } from '@mantine/core'
import { ChevronRight, Database } from 'lucide-react'
import type { Dataset, SyncRun } from '../../../../shared/types'
import { EmptyState } from '../../../../shared/components/EmptyState'
import { formatDateTime, formatRows } from '../../lib/format'
import { RUN_STATUS_LABELS } from '../../lib/labels'

interface DatasetListProps {
  datasets: Dataset[]
  runs: SyncRun[]
  onSelect: (datasetId: string) => void
}

export function DatasetList({ datasets, runs, onSelect }: DatasetListProps) {
  if (!datasets.length) {
    return <EmptyState title="同步成功后，数据集会显示在这里" />
  }

  return (
    <Stack component="ul" className="dataset-list" gap="xs" p={0} style={{ listStyle: 'none' }}>
      {datasets.map((dataset) => (
        <Box component="li" key={dataset.id}>
          <UnstyledButton type="button" className="dataset-card" onClick={() => onSelect(dataset.id)}>
            <ThemeIcon className="dataset-card__icon" variant="light" color="brand" size="lg" radius="md">
              <Database size={18} />
            </ThemeIcon>
            <Stack className="dataset-card__body" gap={2} align="flex-start">
              <Text component="strong" size="sm">{dataset.name}</Text>
              <Text component="span" size="xs" c="dimmed">
                {dataset.source_schema && dataset.source_table
                  ? `${dataset.source_schema}.${dataset.source_table} · `
                  : ''}
                {dataset.current_version
                  ? `v${dataset.current_version.version_number} · ${formatRows(dataset.current_version.row_count)} 行`
                  : '等待首次发布'}
              </Text>
              <Text component="span" size="xs" c="dimmed">
                最近同步：
                {runs.find((run) => run.dataset_id === dataset.id)
                  ? RUN_STATUS_LABELS[runs.find((run) => run.dataset_id === dataset.id)!.status]
                  : '暂无'}
              </Text>
              <Text component="span" size="xs" c="dimmed">更新于 {formatDateTime(dataset.updated_at)}</Text>
            </Stack>
            <Group gap={0} wrap="nowrap"><ChevronRight size={15} /></Group>
          </UnstyledButton>
        </Box>
      ))}
    </Stack>
  )
}
