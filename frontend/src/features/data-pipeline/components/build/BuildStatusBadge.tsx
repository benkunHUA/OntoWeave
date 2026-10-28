import { Badge } from '@mantine/core'
import { buildStatusLabel } from '../../lib/buildStatus'

interface BuildStatusBadgeProps {
  status: string | null | undefined
  subtle?: boolean
}

export function BuildStatusBadge({ status, subtle = false }: BuildStatusBadgeProps) {
  const tone = status?.toLowerCase() ?? 'unknown'
  const color = tone === 'succeeded'
    ? 'teal'
    : tone === 'failed'
      ? 'red'
      : tone === 'running'
        ? 'blue'
        : tone === 'queued'
          ? 'yellow'
          : 'gray'
  return (
    <Badge color={color} variant={subtle ? 'light' : 'filled'} className="pipeline-build-status">
      {buildStatusLabel(status)}
    </Badge>
  )
}
