import { CheckCircle2, CloudUpload } from 'lucide-react'
import type { GraphPublication } from '../../../shared/types'
import { formatPublishedAt, PUBLISH_COPY } from '../lib/publication'

interface GraphPublicationControlProps {
  publication: GraphPublication | null
  isLoading: boolean
  hasError: boolean
  isPublishing: boolean
  blockedReason: string | null
  onPublish: () => void
}

/** 画布顶部的发布状态与按钮；发布前置条件不满足时只禁用，不隐藏原因。 */
export function GraphPublicationControl({
  publication,
  isLoading,
  hasError,
  isPublishing,
  blockedReason,
  onPublish,
}: GraphPublicationControlProps) {
  let stateText: string = PUBLISH_COPY.unpublished
  if (isLoading) stateText = PUBLISH_COPY.reading
  else if (hasError && !publication) stateText = PUBLISH_COPY.unknown
  else if (publication) {
    stateText = `${PUBLISH_COPY.published} ${formatPublishedAt(publication.published_at)}`
  } else if (blockedReason) stateText = blockedReason

  const published = Boolean(publication)
  const disabled = isLoading || isPublishing || Boolean(blockedReason)
  const buttonLabel = isPublishing
    ? PUBLISH_COPY.publishing
    : published
      ? PUBLISH_COPY.republish
      : PUBLISH_COPY.publish

  return (
    <div className="canvas-publish">
      <span
        className={`canvas-publish__state ${published ? 'is-published' : ''} ${
          blockedReason ? 'is-blocked' : ''
        }`}
        title={published ? PUBLISH_COPY.publishedHint : blockedReason ?? undefined}
      >
        {published ? <CheckCircle2 size={12} /> : <CloudUpload size={12} />}
        {stateText}
      </span>
      <button
        type="button"
        className="secondary-button canvas-publish__button"
        disabled={disabled}
        title={blockedReason ?? undefined}
        onClick={onPublish}
      >
        <CloudUpload size={14} />
        {buttonLabel}
      </button>
    </div>
  )
}
