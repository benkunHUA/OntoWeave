import { Database } from 'lucide-react'
import type { PipelineBuildOutput } from '../../../../shared/types'
import { DatasetPreviewTable } from '../../../../shared/components/DatasetPreviewTable'
import { DialogShell } from '../../../modeling/components/dialogs/DialogShell'
import { usePipelineDatasetPreview } from '../../hooks/usePipelineDatasetPreview'
import {
  OUTPUT_DATASET_COPY,
  PIPELINE_DATASET_PREVIEW_LIMIT,
} from '../../lib/executionConstants'
import { formatCount } from '../../lib/format'
import { shortId } from '../../lib/outputs'
import { OutputSourcePanel } from './OutputSourcePanel'

interface OutputDatasetDialogProps {
  pipelineId: string
  output: PipelineBuildOutput
  name: string
  onClose: () => void
  onOpenBuild?: (buildId: string) => void
}

export function OutputDatasetDialog({
  pipelineId,
  output,
  name,
  onClose,
  onOpenBuild,
}: OutputDatasetDialogProps) {
  const { preview, isLoading, error } = usePipelineDatasetPreview({
    pipelineId,
    datasetId: output.dataset_id,
    versionId: output.dataset_version_id,
    limit: PIPELINE_DATASET_PREVIEW_LIMIT,
  })
  if (!output.dataset_id || !output.dataset_version_id) return null
  return (
    <DialogShell
      className="view-dialog--wide"
      icon={<Database size={20} />}
      title={OUTPUT_DATASET_COPY.title}
      subtitle={name}
      onClose={onClose}
      footer={<button type="button" className="secondary-button" onClick={onClose}>{OUTPUT_DATASET_COPY.close}</button>}
    >
      <div className="view-dialog__body pipeline-output-dataset-dialog">
        <dl>
          <div><dt>{OUTPUT_DATASET_COPY.datasetId}</dt><dd>{shortId(output.dataset_id)}</dd></div>
          <div><dt>{OUTPUT_DATASET_COPY.datasetVersionId}</dt><dd>{shortId(output.dataset_version_id)}</dd></div>
        </dl>
        <section className="pipeline-output-dataset-preview">
          <header>
            <span className="eyebrow">{OUTPUT_DATASET_COPY.preview}</span>
            {preview ? (
              <span>{formatCount(preview.row_count)} {OUTPUT_DATASET_COPY.rows}</span>
            ) : null}
          </header>
          <DatasetPreviewTable
            preview={preview}
            loading={isLoading}
            error={error}
            limit={PIPELINE_DATASET_PREVIEW_LIMIT}
          />
        </section>
        <OutputSourcePanel
          pipelineId={pipelineId}
          datasetId={output.dataset_id}
          datasetVersionId={output.dataset_version_id}
          onOpenBuild={onOpenBuild ? (buildId) => {
            onClose()
            onOpenBuild(buildId)
          } : undefined}
        />
      </div>
    </DialogShell>
  )
}
