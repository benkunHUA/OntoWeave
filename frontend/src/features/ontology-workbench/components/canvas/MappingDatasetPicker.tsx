import { NativeSelect, Text } from '@mantine/core'
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
      <NativeSelect
        aria-label={CANVAS_COPY.dataset}
        label={CANVAS_COPY.dataset}
        leftSection={<Database size={13} />}
        value={value}
        disabled={isLoading}
        onChange={(event) => {
          const [kind, id] = event.currentTarget.value.split(':')
          if ((kind === 'SYNC' || kind === 'PIPELINE') && id) onSelect(kind, id)
        }}
        data={[
          { value: '', label: datasets.length ? CANVAS_COPY.chooseDataset : CANVAS_COPY.noDatasets },
          ...(value && !selected ? [{ value, label: `${CANVAS_COPY.boundDataset} · ${selectedId}` }] : []),
          ...datasets.map((item) => ({
            value: datasetValue(item.kind, item.id),
            label: `${item.name} · ${DATASET_KIND_LABELS[item.kind]}`,
          })),
        ]}
        size="xs"
      />
      {selected ? (
        <div className="mapping-dataset-picker__meta">
          <Text component="span" size="xs" c="dimmed">{CANVAS_COPY.datasetVersion} v{selected.version_number ?? '—'}</Text>
          <Text component="span" size="xs" c="dimmed">{selected.row_count.toLocaleString()} {CANVAS_COPY.datasetRows}</Text>
          <Text component="span" size="xs" c="dimmed">{CANVAS_COPY.fieldCount(selected.columns.length)}</Text>
        </div>
      ) : null}
    </div>
  )
}
