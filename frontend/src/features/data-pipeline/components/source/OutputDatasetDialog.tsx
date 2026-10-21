import { Database } from 'lucide-react'
import type { PipelineBuildOutput } from '../../../../shared/types'
import { DialogShell } from '../../../modeling/components/dialogs/DialogShell'
import { OUTPUT_DATASET_COPY } from '../../lib/executionConstants'
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
  if (!output.dataset_id || !output.dataset_version_id) return null
  return (
    <DialogShell
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
