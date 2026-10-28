import { Badge, Code, Group, Paper, Stack, Text, UnstyledButton } from '@mantine/core'
import { ChevronRight, Database, Hash } from 'lucide-react'
import { useState } from 'react'
import { QUERY_COPY } from '../lib/constants'
import type { QueryEvidenceItem } from '../lib/evidence'

interface QueryEvidenceProps {
  evidence: QueryEvidenceItem[]
}

/** task 结果里的结构化证据：默认收起，展开后看对象、步骤和索引版本。 */
export function QueryEvidence({ evidence }: QueryEvidenceProps) {
  const [open, setOpen] = useState(false)
  if (!evidence.length) return null

  return (
    <Paper component="section" className="query-evidence" withBorder radius="md" p="xs">
      <UnstyledButton
        type="button"
        className="query-evidence__toggle"
        aria-expanded={open}
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
      {open ? (
        <Stack className="query-evidence__list" gap="xs" mt="xs">
          {evidence.map((item, index) => (
            <Paper className="query-evidence__item" key={`${item.stepId}-${item.primaryKey}-${index}`} bg="dark.8" p="xs" radius="sm">
              <Group justify="space-between" gap="xs" mb="xs">
                <Text component="strong" size="xs">{item.title || item.primaryKey}</Text>
                <Code>
                  <Hash size={11} />
                  {item.primaryKey}
                </Code>
              </Group>
              <Stack className="query-evidence__facts" gap={3} mt={7}>
                <Group className="query-evidence__row" gap={8} wrap="nowrap">
                  <Text className="query-evidence__label" size="xs" c="dimmed" w={76} truncate>
                    {QUERY_COPY.evidenceStep}
                  </Text>
                  <Text className="query-evidence__value" size="xs" truncate>{item.stepId || '—'}</Text>
                </Group>
                <Group className="query-evidence__row" gap={8} wrap="nowrap">
                  <Text className="query-evidence__label" size="xs" c="dimmed" w={76} truncate>
                    {QUERY_COPY.evidenceIndex}
                  </Text>
                  <Text className="query-evidence__value" size="xs" truncate title={item.indexVersionId ?? undefined}>
                    {shortId(item.indexVersionId)}
                  </Text>
                </Group>
                {Object.entries(item.properties)
                  .slice(0, 4)
                  .map(([key, value]) => (
                    <Group className="query-evidence__row" key={key} gap={8} wrap="nowrap">
                      <Text className="query-evidence__label" size="xs" c="dimmed" w={76} truncate title={key}>
                        {shortId(key)}
                      </Text>
                      <Text className="query-evidence__value" size="xs" truncate>{formatValue(value)}</Text>
                    </Group>
                  ))}
              </Stack>
            </Paper>
          ))}
        </Stack>
      ) : null}
    </Paper>
  )
}

function shortId(value: string | null): string {
  if (!value) return '—'
  return value.length > 8 ? value.slice(0, 8) : value
}

function formatValue(value: unknown): string {
  if (value === null || value === undefined) return '—'
  if (typeof value === 'string') return value
  if (typeof value === 'number' || typeof value === 'boolean') return String(value)
  return JSON.stringify(value)
}
