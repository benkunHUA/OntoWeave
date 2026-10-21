import { buildStatusLabel } from '../../lib/buildStatus'

interface BuildStatusBadgeProps {
  status: string | null | undefined
  subtle?: boolean
}

export function BuildStatusBadge({ status, subtle = false }: BuildStatusBadgeProps) {
  const tone = status?.toLowerCase() ?? 'unknown'
  return (
    <span className={`pipeline-build-status is-${tone}${subtle ? ' is-subtle' : ''}`}>
      {buildStatusLabel(status)}
    </span>
  )
}
