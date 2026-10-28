import { Alert, Button, Group } from '@mantine/core'
import { AlertTriangle, CircleAlert, Info, RefreshCw } from 'lucide-react'
import type { MappingIssue } from '../../../../shared/types'
import type { MappingFeedback } from './useMappingDraft'
import { CANVAS_COPY } from './copy'

interface MappingBannersProps {
  error: string | null
  conflict: string | null
  feedback: MappingFeedback | null
  onRetry: () => void
  onDismissFeedback: () => void
}

export function MappingBanners(props: MappingBannersProps) {
  return (
    <>
      {props.conflict ? (
        <Alert className="mapping-banner" color="red" variant="light" icon={<CircleAlert size={15} />} role="alert">
          <Group justify="space-between" gap="sm">
            <span>{props.conflict}</span>
            <Button type="button" variant="subtle" size="xs" leftSection={<RefreshCw size={12} />} onClick={props.onRetry}>{CANVAS_COPY.retry}</Button>
          </Group>
        </Alert>
      ) : null}
      {props.error ? (
        <Alert className="mapping-banner" color="red" variant="light" icon={<CircleAlert size={15} />} role="alert">
          <Group justify="space-between" gap="sm">
            <span>{props.error}</span>
            <Button type="button" variant="subtle" size="xs" leftSection={<RefreshCw size={12} />} onClick={props.onRetry}>{CANVAS_COPY.retry}</Button>
          </Group>
        </Alert>
      ) : null}
      {props.feedback ? (
        <Alert
          className="mapping-banner"
          color={props.feedback.tone === 'error' ? 'red' : 'blue'}
          variant="light"
          icon={props.feedback.tone === 'error' ? <CircleAlert size={15} /> : <Info size={15} />}
          role="status"
          withCloseButton
          closeButtonLabel={CANVAS_COPY.close}
          onClose={props.onDismissFeedback}
        >
          {props.feedback.message}
        </Alert>
      ) : null}
    </>
  )
}

interface MappingIssuePanelProps {
  blocking: MappingIssue[]
  warnings: MappingIssue[]
}

export function MappingIssuePanel({ blocking, warnings }: MappingIssuePanelProps) {
  if (!blocking.length && !warnings.length) return null
  return (
    <div className="mapping-issues" aria-label={CANVAS_COPY.localIssues}>
      {blocking.length ? (
        <div className="mapping-issues__group is-blocking">
          <strong><CircleAlert size={12} />{CANVAS_COPY.blockingCount(blocking.length)}</strong>
          <ul>{blocking.slice(0, 4).map((item, index) => <li key={`${item.code}:${index}`}>{item.message}</li>)}</ul>
        </div>
      ) : null}
      {warnings.length ? (
        <div className="mapping-issues__group is-warning">
          <strong><AlertTriangle size={12} />{CANVAS_COPY.warningCount(warnings.length)}</strong>
          <ul>{warnings.slice(0, 3).map((item, index) => <li key={`${item.code}:${index}`}>{item.message}</li>)}</ul>
        </div>
      ) : null}
    </div>
  )
}
