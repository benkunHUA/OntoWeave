import { Button, Group, Paper, Text, ThemeIcon } from '@mantine/core'
import { CircleCheck, CircleX, LoaderCircle, Sparkles } from 'lucide-react'

interface ChangesetBarProps {
  /** 「新增 2 · 修改 1」式的改动摘要，非空。 */
  summary: string[]
  isConfirming: boolean
  onReject: () => void
  onConfirm: () => void
}

/** 底部确认条：列出本轮待确认的本体改动，给「撤销 / 确认」两个出口。 */
export function ChangesetBar({ summary, isConfirming, onReject, onConfirm }: ChangesetBarProps) {
  return (
    <Paper component="section" className="changeset-bar" withBorder radius="md" p="sm">
      <Group className="changeset-bar__summary" gap="sm" wrap="nowrap">
        <ThemeIcon className="changeset-bar__icon" size="md" radius="md" variant="light" color="brand">
          <Sparkles size={16} />
        </ThemeIcon>
        <StackSmall summary={summary} />
      </Group>
      <Group className="changeset-bar__actions" gap="xs" wrap="nowrap">
        <Button type="button" variant="default" size="xs" leftSection={<CircleX size={15} />} onClick={onReject}>
          撤销
        </Button>
        <Button
          type="button"
          size="xs"
          onClick={onConfirm}
          disabled={isConfirming}
          leftSection={isConfirming ? <LoaderCircle size={15} /> : <CircleCheck size={15} />}
        >
          {isConfirming ? '确认中' : '确认'}
        </Button>
      </Group>
    </Paper>
  )
}

function StackSmall({ summary }: { summary: string[] }) {
  return (
    <div className="changeset-bar__summary-copy">
      <Text component="strong" className="changeset-bar__summary-title" size="xs">
        待确认改动
      </Text>
      <Text component="span" className="changeset-bar__summary-detail" size="xs" c="dimmed">
        {summary.join(' · ')}
      </Text>
    </div>
  )
}
