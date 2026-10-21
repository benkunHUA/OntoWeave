import { useEffect, useState } from 'react'
import { listPipelineInputDatasets } from '../../../../../shared/api'
import { httpErrorText } from '../../../../../shared/api/errors'
import type { InputDataset } from '../../../../../shared/types'

interface DatasetPickerFieldProps {
  value: unknown
  onChange: (value: string) => void
}

export function DatasetPickerField({ value, onChange }: DatasetPickerFieldProps) {
  const [datasets, setDatasets] = useState<InputDataset[]>([])
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    void listPipelineInputDatasets(200, 0)
      .then((page) => {
        if (active) setDatasets(page.items)
      })
      .catch((reason: unknown) => {
        if (active) setError(httpErrorText(reason, '读取数据集失败'))
      })
    return () => {
      active = false
    }
  }, [])

  const selected = typeof value === 'string' ? value : ''

  return (
    <div className="pipeline-dataset-picker">
      <select value={selected} onChange={(event) => onChange(event.target.value)}>
        <option value="">选择同步数据集</option>
        {selected && !datasets.some((dataset) => dataset.id === selected) ? (
          <option value={selected}>{selected}</option>
        ) : null}
        {datasets.map((dataset) => (
          <option key={dataset.id} value={dataset.id}>
            {dataset.name}
            {dataset.current_version ? ` · v${dataset.current_version.version_number}` : ''}
          </option>
        ))}
      </select>
      {error ? <p className="pipeline-field-error">{error}</p> : null}
    </div>
  )
}
