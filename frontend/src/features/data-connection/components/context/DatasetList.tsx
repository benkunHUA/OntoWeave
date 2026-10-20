import { ChevronRight, Database } from 'lucide-react'
import type { Dataset, SyncRun } from '../../../../shared/types'
import { formatDateTime, formatRows } from '../../lib/format'
import { RUN_STATUS_LABELS } from '../../lib/labels'

interface DatasetListProps {
  datasets: Dataset[]
  runs: SyncRun[]
  onSelect: (datasetId: string) => void
}

export function DatasetList({ datasets, runs, onSelect }: DatasetListProps) {
  if (!datasets.length) {
    return <div className="empty-panel">同步成功后，数据集会显示在这里</div>
  }

  return (
    <ul className="dataset-list">
      {datasets.map((dataset) => (
        <li key={dataset.id}>
          <button type="button" className="dataset-card" onClick={() => onSelect(dataset.id)}>
            <span className="dataset-card__icon"><Database size={18} /></span>
            <span className="dataset-card__body">
              <strong>{dataset.name}</strong>
              <span>
                {dataset.source_schema && dataset.source_table
                  ? `${dataset.source_schema}.${dataset.source_table} · `
                  : ''}
                {dataset.current_version
                  ? `v${dataset.current_version.version_number} · ${formatRows(dataset.current_version.row_count)} 行`
                  : '等待首次发布'}
              </span>
              <span>
                最近同步：
                {runs.find((run) => run.dataset_id === dataset.id)
                  ? RUN_STATUS_LABELS[runs.find((run) => run.dataset_id === dataset.id)!.status]
                  : '暂无'}
              </span>
              <span>更新于 {formatDateTime(dataset.updated_at)}</span>
            </span>
            <ChevronRight size={15} />
          </button>
        </li>
      ))}
    </ul>
  )
}
