import { Stack, Text, ThemeIcon } from '@mantine/core'
import type { ReactNode } from 'react'

interface EmptyStateProps {
  icon?: ReactNode
  title: string
  body?: string
  className?: string
}

/** 列表、聊天与数据面板的共享空态，统一图标、层级和留白。 */
export function EmptyState({ icon, title, body, className }: EmptyStateProps) {
  return (
    <Stack className={className} align="center" justify="center" gap="xs" px="md" py="xl">
      {icon ? (
        <ThemeIcon variant="light" color="gray" size="lg" radius="xl" aria-hidden="true">
          {icon}
        </ThemeIcon>
      ) : null}
      <Text fw={600} size="sm">
        {title}
      </Text>
      {body ? (
        <Text size="xs" c="dimmed" ta="center" maw={360}>
          {body}
        </Text>
      ) : null}
    </Stack>
  )
}
