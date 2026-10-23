import { Database } from 'lucide-react'
import type { DatasetKind, OntologyDataset } from '../../../../shared/types'
import { CANVAS_COPY, DATASET_KIND_LABELS } from './copy'

interface MappingDatasetPickerProps {
  datasets: OntologyDataset[]
  selectedKind: DatasetKind | null
  selectedId: string | null
  isLoading: boolean
  onSelect: (kind: DatasetKind, id: string) => void
}

function datasetValue(kind: DatasetKind | null, id: string | null): string {
  return kind && id ? `${kind}:${id}` : ''
}

export function MappingDatasetPicker(props: MappingDatasetPickerProps) {
  const { datasets, selectedKind, selectedId, isLoading, onSelect } = props
  const value = datasetValue(selectedKind, selectedId)
  const selected = datasets.find((item) => item.kind === selectedKind && item.id === selectedId)
  return (
    <div className="mapping-dataset-picker">
      <label>
        <span><Database size={13} />{CANVAS_COPY.dataset}</span>
        <select
          aria-label={CANVAS_COPY.dataset}
          value={value}
          disabled={isLoading}
          onChange={(event) => {
            const [kind, id] = event.target.value.split(':')
            if ((kind === 'SYNC' || kind === 'PIPELINE') && id) onSelect(kind, id)
          }}
        >
          <option value="">{datasets.length ? CANVAS_COPY.chooseDataset : CANVAS_COPY.noDatasets}</option>
          {value && !selected ? <option value={value}>{CANVAS_COPY.boundDataset} · {selectedId}</option> : null}
          {datasets.map((item) => (
            <option key={`${item.kind}:${item.id}`} value={datasetValue(item.kind, item.id)}>
              {item.name} · {DATASET_KIND_LABELS[item.kind]}
            </option>
          ))}
        </select>
      </label>
      {selected ? (
        <div className="mapping-dataset-picker__meta">
          <span>{CANVAS_COPY.datasetVersion} v{selected.version_number ?? '—'}</span>
          <span>{selected.row_count.toLocaleString()} {CANVAS_COPY.datasetRows}</span>
          <span>{CANVAS_COPY.fieldCount(selected.columns.length)}</span>
        </div>
      ) : null}
    </div>
  )
}
