import { Alert, Badge, Button, Group, Paper, Stack, Table, Text } from '@mantine/core'
import { ArrowRight, CircleAlert } from 'lucide-react'
import type {
  ActionConfirmation,
  ActionObjectChange,
  ActionRelationChange,
  InterruptOption,
} from '../../../shared/types'
import {
  ACTION_COPY,
  changeLabel,
  formatActionValue,
  isApproveOption,
} from '../lib/actionCopy'

interface ActionConfirmationCardProps {
  confirmation: ActionConfirmation
  pending: boolean
  disabled: boolean
  onAnswer: (option: InterruptOption) => void
}

/** 行动执行的富确认卡片：先看清对象、字段与关系变更，再决定是否写入。 */
export function ActionConfirmationCard({
  confirmation,
  pending,
  disabled,
  onAnswer,
}: ActionConfirmationCardProps) {
  return (
    <Paper className={`action-confirmation ${pending ? 'is-pending' : ''}`} withBorder radius="md">
      <Stack gap="sm">
        <Alert
          icon={<CircleAlert size={15} />}
          color="yellow"
          variant="light"
          p="xs"
          title={confirmation.title}
        >
          {confirmation.action.description || ACTION_COPY.alertFallback}
        </Alert>

        <Group className="action-confirmation__summary" gap="xs" wrap="wrap">
          <Text component="span" size="sm">{confirmation.summary}</Text>
          <Badge color="yellow" variant="light">{confirmation.action.name}</Badge>
        </Group>

        {confirmation.warnings.length ? (
          <Stack className="action-confirmation__warnings" gap={4}>
            <Text size="xs" fw={700}>{ACTION_COPY.warnings}</Text>
            {confirmation.warnings.map((warning, index) => (
              <Text key={index} size="xs" c="dimmed">{formatActionValue(warning)}</Text>
            ))}
          </Stack>
        ) : null}

        {confirmation.object_changes.length ? (
          <section className="action-confirmation__section">
            <Text className="action-confirmation__section-title" size="xs" fw={700}>
              {ACTION_COPY.objectChanges}
            </Text>
            <Stack gap="xs">
              {confirmation.object_changes.map((change, index) => (
                <ObjectChange key={`${change.object_type_id}-${change.primary_key}-${index}`} change={change} />
              ))}
            </Stack>
          </section>
        ) : null}

        {confirmation.relation_changes.length ? (
          <section className="action-confirmation__section">
            <Text className="action-confirmation__section-title" size="xs" fw={700}>
              {ACTION_COPY.relationChanges}
            </Text>
            <Stack gap="xs">
              {confirmation.relation_changes.map((change, index) => (
                <RelationChange key={`${change.relation_id}-${index}`} change={change} />
              ))}
            </Stack>
          </section>
        ) : null}

        <Group className="action-confirmation__actions" gap="xs">
          {confirmation.options.map((option) => (
            <Button
              key={option.label}
              type="button"
              size="xs"
              variant={isApproveOption(option.answer) ? 'filled' : 'light'}
              color={isApproveOption(option.answer) ? 'yellow' : 'gray'}
              onClick={() => onAnswer(option)}
              disabled={!pending || disabled}
            >
              {option.label}
            </Button>
          ))}
        </Group>

        {pending ? (
          <Text size="xs" c="dimmed">
            {disabled ? '正在提交，请稍候。' : ACTION_COPY.pendingHint}
          </Text>
        ) : null}
      </Stack>
    </Paper>
  )
}

function ObjectChange({ change }: { change: ActionObjectChange }) {
  return (
    <Paper className="action-confirmation__object" bg="dark.8" p="xs" radius="sm">
      <Group justify="space-between" gap="xs" wrap="wrap">
        <Group gap="xs" wrap="wrap">
          <Badge size="xs" color={change.kind === 'OBJECT_DELETE' ? 'red' : 'teal'} variant="light">
            {changeLabel(change.kind)}
          </Badge>
          <Text size="xs" fw={600}>{change.object_type_name || ACTION_COPY.objectType}</Text>
          <Text size="xs">{change.title}</Text>
        </Group>
        <Text size="xs" c="dimmed">{ACTION_COPY.primaryKey} {change.primary_key}</Text>
      </Group>
      {change.fields.length ? (
        <Table className="action-confirmation__fields" mt="xs" verticalSpacing={4} horizontalSpacing="xs">
          <Table.Thead>
            <Table.Tr>
              <Table.Th>{ACTION_COPY.field}</Table.Th>
              <Table.Th>{ACTION_COPY.before}</Table.Th>
              <Table.Th>{ACTION_COPY.after}</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {change.fields.map((field, index) => (
              <Table.Tr key={`${field.property_id ?? field.name ?? 'field'}-${index}`}>
                <Table.Td>{field.name || field.property_id}</Table.Td>
                <Table.Td>{formatActionValue(field.before)}</Table.Td>
                <Table.Td>{formatActionValue(field.after)}</Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      ) : (
        <Text size="xs" c="dimmed" mt={4}>{ACTION_COPY.noFields}</Text>
      )}
    </Paper>
  )
}

function RelationChange({ change }: { change: ActionRelationChange }) {
  return (
    <Paper className="action-confirmation__relation" bg="dark.8" p="xs" radius="sm">
      <Group justify="space-between" gap="xs" wrap="wrap">
        <Group gap="xs" wrap="wrap">
          <Badge size="xs" color={change.kind === 'RELATION_DELETE' ? 'red' : 'blue'} variant="light">
            {changeLabel(change.kind)}
          </Badge>
          <Text size="xs" fw={600}>{change.relation_name || change.relation_id}</Text>
        </Group>
      </Group>
      <Group className="action-confirmation__relation-path" gap={6} mt={4} wrap="wrap">
        <Text size="xs">
          {change.from_object_type_name || change.from_object_type_id} {change.from_primary_key}
        </Text>
        <ArrowRight size={13} />
        <Text size="xs">
          {change.to_object_type_name || change.to_object_type_id} {change.to_primary_key}
        </Text>
      </Group>
    </Paper>
  )
}
