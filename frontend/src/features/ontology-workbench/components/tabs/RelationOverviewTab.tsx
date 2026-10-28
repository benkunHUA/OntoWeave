import { Alert, Badge, Box, Group, Loader, Paper, Stack, Text, ThemeIcon } from '@mantine/core'
import { ArrowRight, KeyRound, Link2 } from 'lucide-react'
import { useEffect, useState } from 'react'
import { httpErrorText } from '../../../../shared/api/errors'
import { getOntologyResourceDefinition } from '../../../../shared/api/ontology'
import type { OntologyResourceDefinition } from '../../../../shared/types'
import { PROPERTY_DATATYPE_LABELS } from '../../lib/mapping'
import { CANVAS_COPY } from '../canvas/copy'
import type { ResourceTabProps } from './types'

export function RelationOverviewTab({ resource }: ResourceTabProps) {
  const [loaded, setLoaded] = useState<{
    id: string
    definition: OntologyResourceDefinition | null
    error: string | null
  } | null>(null)

  useEffect(() => {
    let active = true
    void getOntologyResourceDefinition('relation', resource.id)
      .then((definition) => {
        if (active) setLoaded({ id: resource.id, definition, error: null })
      })
      .catch((reason) => {
        if (active) {
          setLoaded({
            id: resource.id,
            definition: null,
            error: httpErrorText(reason, CANVAS_COPY.overviewLoadError),
          })
        }
      })
    return () => { active = false }
  }, [resource.id])

  const current = loaded?.id === resource.id ? loaded : null
  const definition = current?.definition ?? null
  const error = current?.error ?? null
  if (error) {
    return <Alert color="red" variant="light" title={CANVAS_COPY.overviewTitle}>{error}</Alert>
  }
  if (!definition) {
    return (
      <Group className="relation-overview relation-overview--state" justify="center" gap="xs">
        <Loader size="sm" />
        <Text size="xs" c="dimmed">{CANVAS_COPY.definitionLoading}</Text>
      </Group>
    )
  }

  const sourcePrimary = definition.source?.properties.find((item) => item.is_primary_key)
  const targetPrimary = definition.target?.properties.find((item) => item.is_primary_key)
  const foreignKey = definition.foreign_key_property
  return (
    <Stack component="section" className="relation-overview" gap="md" aria-label={CANVAS_COPY.overviewTitle}>
      <Text size="xs" c="dimmed">{CANVAS_COPY.overviewBody}</Text>
      <Box className="relation-overview__flow">
        <Paper className="relation-overview__node" withBorder p="md" radius="lg">
          <ThemeIcon className="relation-overview__icon" size={30} radius="md" variant="light" color="brand">
            <KeyRound size={16} />
          </ThemeIcon>
          <Text size="xs" c="dimmed">{CANVAS_COPY.sourceObject}</Text>
          <Text size="sm" fw={600}>{definition.source?.name ?? definition.source_type_name ?? '—'}</Text>
          <Text size="xs" c="dimmed">{CANVAS_COPY.foreignKeyAttribute(foreignKey?.name ?? CANVAS_COPY.noForeignKey)}</Text>
          <Badge variant="light" color="brand" size="xs">
            {foreignKey ? PROPERTY_DATATYPE_LABELS[foreignKey.datatype] : CANVAS_COPY.noLabel}
          </Badge>
        </Paper>
        <Box className="relation-overview__arrow">
          <ArrowRight size={18} />
          <Text size="xs" c="dimmed">{definition.forward_label ?? '指向'}</Text>
        </Box>
        <Paper className="relation-overview__node" withBorder p="md" radius="lg">
          <ThemeIcon className="relation-overview__icon" size={30} radius="md" variant="light" color="brand">
            <Link2 size={16} />
          </ThemeIcon>
          <Text size="xs" c="dimmed">{CANVAS_COPY.targetObject}</Text>
          <Text size="sm" fw={600}>{definition.target?.name ?? definition.target_type_name ?? '—'}</Text>
          <Text size="xs" c="dimmed">{CANVAS_COPY.targetPrimaryKey(targetPrimary?.name ?? CANVAS_COPY.noPrimaryKey)}</Text>
          <Badge variant="light" color="brand" size="xs">
            {targetPrimary ? PROPERTY_DATATYPE_LABELS[targetPrimary.datatype] : CANVAS_COPY.noLabel}
          </Badge>
        </Paper>
      </Box>
      <Group className="relation-overview__meta" gap="xs">
        <Badge variant="light" color="gray" size="xs">{CANVAS_COPY.sourcePrimaryMeta(
          sourcePrimary?.name ?? CANVAS_COPY.noLabel,
          sourcePrimary ? PROPERTY_DATATYPE_LABELS[sourcePrimary.datatype] : CANVAS_COPY.noLabel,
        )}</Badge>
        <Badge variant="light" color="gray" size="xs">
          {CANVAS_COPY.cardinalityMeta(definition.cardinality ?? resource.cardinality ?? CANVAS_COPY.noLabel)}
        </Badge>
        <Badge variant="light" color="gray" size="xs">{CANVAS_COPY.statusMeta(definition.status)}</Badge>
      </Group>
    </Stack>
  )
}
