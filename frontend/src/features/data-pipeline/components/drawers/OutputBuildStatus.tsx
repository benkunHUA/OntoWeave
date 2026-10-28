import { Anchor, Button, Group, Text } from '@mantine/core'
import { ExternalLink } from 'lucide-react'
import type { PipelineBuild } from '../../../../shared/types'
import { buildStatusLabel, outputElapsedLabel } from '../../lib/buildStatus'
import { BUILD_COPY, OUTPUT_BUILD_COPY } from '../../lib/executionConstants'

interface OutputBuildStatusProps {
  build: PipelineBuild | null | undefined
  outputId: string | null
  onOpenBuildDetails?: (buildId: string) => void
}

export function OutputBuildStatus({
  build,
  outputId,
  onOpenBuildDetails,
}: OutputBuildStatusProps) {
  if (!build || !outputId) return null
  const output = build.outputs.find((item) => item.output_id === outputId)
  const logUri = output?.log_uri ?? build.log_uri
  return (
    <section className="pipeline-output-build" aria-label={OUTPUT_BUILD_COPY.ariaLabel}>
      <header>
        <Text component="strong" size="xs">{OUTPUT_BUILD_COPY.title}</Text>
        <Text component="span" size="xs" c="dimmed">{output ? buildStatusLabel(output.status) : OUTPUT_BUILD_COPY.missingOutput}</Text>
      </header>
      {output && outputElapsedLabel(output)
        ? <Text component="small" size="xs" c="dimmed">{OUTPUT_BUILD_COPY.elapsed} {outputElapsedLabel(output)}</Text>
        : null}
      <Group gap="xs">
        {onOpenBuildDetails ? (
          <Button type="button" variant="subtle" size="compact-xs" onClick={() => onOpenBuildDetails(build.id)}>
            {BUILD_COPY.viewDetails}
          </Button>
        ) : null}
        {logUri ? (
          <Anchor className="pipeline-build-log-link" href={logUri} target="_blank" rel="noreferrer" size="xs">
            {OUTPUT_BUILD_COPY.log} <ExternalLink size={11} />
          </Anchor>
        ) : <Text component="small" size="xs" c="dimmed">{BUILD_COPY.logMissing}</Text>}
      </Group>
    </section>
  )
}
