import {
  Badge,
  Code,
  Collapse,
  Divider,
  Group,
  Paper,
  Stack,
  Text,
  UnstyledButton,
} from '@mantine/core'
import { ChevronRight, Database, Hash } from 'lucide-react'
import { useId, useState } from 'react'
import { useEvidenceMetadata, type EvidenceTypeMetadata } from '../hooks/useEvidenceMetadata'
import { QUERY_COPY } from '../lib/constants'
import type { QueryEvidenceItem } from '../lib/evidence'

interface QueryEvidenceProps {
  evidence: QueryEvidenceItem[]
}

/** query_objects 结果里的证据：默认收起，展开后看业务对象、字段和索引版本。 */
export function QueryEvidence({ evidence }: QueryEvidenceProps) {
  const [open, setOpen] = useState(false)
  const panelId = useId()
  const metadata = useEvidenceMetadata(evidence)
  if (!evidence.length) return null

  return (
    <Paper component="section" className="query-evidence" withBorder radius="md" p="xs">
      <UnstyledButton
        type="button"
        className="query-evidence__toggle"
        aria-expanded={open}
        aria-controls={panelId}
        onClick={() => setOpen((value) => !value)}
        w="100%"
      >
        <Group gap={6} wrap="nowrap">
          <Database size={13} />
          <Text component="span" size="xs" fw={600}>{QUERY_COPY.evidenceTitle}</Text>
          <Badge size="xs" variant="light" color="gray">{evidence.length}</Badge>
          <ChevronRight size={13} className={open ? 'is-open' : ''} />
        </Group>
      </UnstyledButton>
      <Collapse id={panelId} expanded={open}>
        <Stack className="query-evidence__list" gap="xs" mt="xs">
          {evidence.map((item, index) => (
            <EvidenceCard
              key={`${item.objectTypeId}-${item.primaryKey}-${index}`}
              item={item}
              metadata={metadata[item.objectTypeId]}
            />
          ))}
        </Stack>
      </Collapse>
    </Paper>
  )
}

function EvidenceCard({
  item,
  metadata,
}: {
  item: QueryEvidenceItem
  metadata: EvidenceTypeMetadata | undefined
}) {
  const typeName = objectTypeName(item, metadata)
  const title = cardTitle(item, typeName)
  const subtitle = cardTypeName(item, typeName)
  const properties = Object.entries(item.properties)

  return (
    <Paper className="query-evidence__item" bg="dark.8" p="sm" radius="sm">
      <Group justify="space-between" align="flex-start" gap="sm" wrap="nowrap">
        <Stack gap={2} miw={0}>
          <Text className="query-evidence__title" component="strong" size="sm" title={title}>
            {title}
          </Text>
          {subtitle ? <Text size="xs" c="dimmed">{subtitle}</Text> : null}
        </Stack>
        <Code
          className="query-evidence__primary-key"
          title={`${QUERY_COPY.evidencePrimaryKey} ${item.primaryKey}`}
        >
          <Hash size={11} />
          {item.primaryKey}
        </Code>
      </Group>
      <Divider my={9} color="dark.6" />
      {properties.length ? (
        <Stack gap={5}>
          {properties.map(([key, value]) => {
            const label = propertyLabel(item, metadata, key)
            const formatted = formatValue(value)
            return (
              <Group
                className="query-evidence__row"
                key={key}
                gap="sm"
                align="flex-start"
                wrap="nowrap"
              >
                <Text className="query-evidence__label" size="xs" c="dimmed" title={label}>
                  {label}
                </Text>
                <Text className="query-evidence__value" size="xs" title={formatted}>
                  {formatted}
                </Text>
              </Group>
            )
          })}
        </Stack>
      ) : (
        <Text size="xs" c="dimmed">{QUERY_COPY.evidenceNoProperties}</Text>
      )}
      <Group className="query-evidence__meta" gap="md" wrap="wrap">
        <Text size="xs" c="dimmed" title={item.indexVersionId ?? undefined}>
          {QUERY_COPY.evidenceIndexVersion} {shortId(item.indexVersionId)}
        </Text>
      </Group>
    </Paper>
  )
}

function objectTypeName(
  item: QueryEvidenceItem,
  metadata: EvidenceTypeMetadata | undefined,
): string | null {
  return cleanText(item.objectTypeName) ?? cleanText(metadata?.name)
}

function cardTitle(item: QueryEvidenceItem, typeName: string | null): string {
  const title = cleanText(item.title)
  if (!title || (title === item.primaryKey && typeName)) {
    return typeName ?? QUERY_COPY.evidenceObjectFallback
  }
  return title
}

function cardTypeName(item: QueryEvidenceItem, typeName: string | null): string | null {
  const title = cleanText(item.title)
  return title && title !== item.primaryKey && typeName !== title ? typeName : null
}

function propertyLabel(
  item: QueryEvidenceItem,
  metadata: EvidenceTypeMetadata | undefined,
  key: string,
): string {
  return cleanText(item.propertyNames[key])
    ?? cleanText(metadata?.propertyNames[key])
    ?? `${QUERY_COPY.evidenceFieldFallback} ${shortId(key)}`
}

function cleanText(value: string | null | undefined): string | null {
  const text = value?.trim()
  return text ? text : null
}

function shortId(value: string | null): string {
  if (!value) return '—'
  return value.length > 8 ? value.slice(0, 8) : value
}

function formatValue(value: unknown): string {
  if (value === null || value === undefined) return '—'
  if (typeof value === 'string') return value.trim() || '—'
  if (typeof value === 'boolean') return value ? QUERY_COPY.trueValue : QUERY_COPY.falseValue
  if (typeof value === 'number') return Number.isFinite(value) ? String(value) : '—'
  if (Array.isArray(value)) return value.length ? value.map(formatValue).join('、') : '—'
  return JSON.stringify(value)
}
