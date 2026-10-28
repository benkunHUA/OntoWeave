import { Button, Group, Paper, Text, Title } from '@mantine/core'
import { CANVAS_COPY } from './copy'

interface MappingConfirmDialogProps {
  title: string
  body: string
  onCancel: () => void
  onConfirm: () => void
}

export function MappingConfirmDialog(props: MappingConfirmDialogProps) {
  return (
    <div className="mapping-confirm-backdrop" role="presentation">
      <Paper className="mapping-confirm" role="dialog" aria-modal="true" aria-labelledby="mapping-confirm-title" withBorder radius="lg" p="lg">
        <Title id="mapping-confirm-title" order={3}>{props.title}</Title>
        <Text size="sm" c="dimmed" mt="xs">{props.body}</Text>
        <Group className="mapping-confirm__actions" justify="flex-end" gap="xs" mt="md">
          <Button type="button" variant="default" onClick={props.onCancel}>{CANVAS_COPY.cancel}</Button>
          <Button type="button" onClick={props.onConfirm}>{CANVAS_COPY.confirm}</Button>
        </Group>
      </Paper>
    </div>
  )
}
