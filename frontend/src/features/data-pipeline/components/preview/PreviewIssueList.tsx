import { Text, Title, UnstyledButton } from '@mantine/core'
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
      <Title order={4}>{PREVIEW_COPY.errors}</Title>
      {issues.map((issue, index) => (
        <UnstyledButton
          key={`${issue.code ?? 'issue'}-${issue.node_id ?? 'node'}-${index}`}
          type="button"
          className={`pipeline-preview-issue ${issue.level === 'warning' ? 'is-warning' : 'is-error'}`}
          onClick={() => onSelectIssue(issue)}
          disabled={!issue.node_id}
        >
          {issue.level === 'warning' ? <Info size={14} /> : <CircleAlert size={14} />}
          <span>
            <Text component="strong" size="xs">{issue.message ?? issue.code ?? '预览错误'}</Text>
            <Text component="small" size="xs" c="dimmed">
              {[issue.code, issue.field ? `字段 ${issue.field}` : null, issue.hint]
                .filter(Boolean)
                .join(' · ')}
            </Text>
          </span>
        </UnstyledButton>
      ))}
    </section>
  )
}
