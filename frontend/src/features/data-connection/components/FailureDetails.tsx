import { Badge, Code, Group, Text, UnstyledButton } from '@mantine/core'
import { ChevronRight } from 'lucide-react'
import { useId, useState } from 'react'

interface FailureDetailsProps {
  message: string
  code?: string | null
}

export function FailureDetails({ message, code }: FailureDetailsProps) {
  const [open, setOpen] = useState(false)
  const contentId = useId()

  return (
    <div className="failure-details">
      <UnstyledButton
        type="button"
        className="failure-details__toggle"
        aria-controls={contentId}
        aria-expanded={open}
        onClick={() => setOpen((value) => !value)}
      >
        <Group gap={6} wrap="nowrap">
          <ChevronRight size={12} className={open ? 'is-open' : ''} />
          <Text component="span" size="xs">失败详情</Text>
          {code ? <Badge size="xs" color="red" variant="light">{code}</Badge> : null}
        </Group>
      </UnstyledButton>
      {open ? (
        <Code id={contentId} className="failure-details__content" block mt="xs">{message}</Code>
      ) : null}
    </div>
  )
}
