import { ChevronRight, Database } from 'lucide-react'
import type { Dataset } from '../../../../shared/types'
import { formatDateTime, formatRows } from '../../lib/format'

interface DatasetListProps {
  datasets: Dataset[]
  onSelect: (datasetId: string) => void
}

export function DatasetList({ datasets, onSelect }: DatasetListProps) {
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
                {dataset.current_version
                  ? `v${dataset.current_version.version_number} · ${formatRows(dataset.current_version.row_count)} 行`
                  : '等待首次发布'}
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
