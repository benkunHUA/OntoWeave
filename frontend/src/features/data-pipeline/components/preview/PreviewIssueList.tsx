import { CircleAlert, Info } from 'lucide-react'
import type { PipelinePreviewIssue } from '../../../../shared/types'
import { PREVIEW_COPY } from '../../lib/executionConstants'

interface PreviewIssueListProps {
  issues: PipelinePreviewIssue[]
  onSelectIssue: (issue: PipelinePreviewIssue) => void
}

export function PreviewIssueList({ issues, onSelectIssue }: PreviewIssueListProps) {
  if (!issues.length) return null
  return (
    <section className="pipeline-preview-issues" aria-label={PREVIEW_COPY.errors}>
      <h3>{PREVIEW_COPY.errors}</h3>
      {issues.map((issue, index) => (
        <button
          key={`${issue.code ?? 'issue'}-${issue.node_id ?? 'node'}-${index}`}
          type="button"
          className={`pipeline-preview-issue ${issue.level === 'warning' ? 'is-warning' : 'is-error'}`}
          onClick={() => onSelectIssue(issue)}
          disabled={!issue.node_id}
        >
          {issue.level === 'warning' ? <Info size={14} /> : <CircleAlert size={14} />}
          <span>
            <strong>{issue.message ?? issue.code ?? '预览错误'}</strong>
            <small>
              {[issue.code, issue.field ? `字段 ${issue.field}` : null, issue.hint]
                .filter(Boolean)
                .join(' · ')}
            </small>
          </span>
        </button>
      ))}
    </section>
  )
}
