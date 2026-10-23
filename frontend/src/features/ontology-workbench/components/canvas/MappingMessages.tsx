import { AlertTriangle, CircleAlert, Info, RefreshCw, X } from 'lucide-react'
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
        <div className="mapping-banner is-error" role="alert">
          <CircleAlert size={15} />
          <span>{props.conflict}</span>
          <button type="button" className="text-button" onClick={props.onRetry}><RefreshCw size={12} />{CANVAS_COPY.retry}</button>
        </div>
      ) : null}
      {props.error ? (
        <div className="mapping-banner is-error" role="alert">
          <CircleAlert size={15} />
          <span>{props.error}</span>
          <button type="button" className="text-button" onClick={props.onRetry}><RefreshCw size={12} />{CANVAS_COPY.retry}</button>
        </div>
      ) : null}
      {props.feedback ? (
        <div className={`mapping-banner is-${props.feedback.tone}`} role="status">
          {props.feedback.tone === 'error' ? <CircleAlert size={15} /> : <Info size={15} />}
          <span>{props.feedback.message}</span>
          <button type="button" className="text-button" onClick={props.onDismissFeedback} aria-label={CANVAS_COPY.close}><X size={12} /></button>
        </div>
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
