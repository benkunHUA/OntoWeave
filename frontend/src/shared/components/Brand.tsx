import { Group, Stack, Text, ThemeIcon } from '@mantine/core'
import type { ReactNode } from 'react'

interface BrandProps {
  icon: ReactNode
  title: string
  subtitle: string
  compact?: boolean
}

/** 应用品牌头：首页与各工作台左栏共用同一套图标、字重与间距。 */
export function Brand({ icon, title, subtitle, compact = false }: BrandProps) {
  return (
    <Group gap={compact ? 8 : 12} wrap="nowrap">
      <ThemeIcon
        size={compact ? 34 : 42}
        radius="md"
        variant="light"
        color="brand"
        aria-hidden="true"
      >
        {icon}
      </ThemeIcon>
      <Stack gap={1}>
        <Text fw={700} size={compact ? 'sm' : 'md'} lh={1.2}>
          {title}
        </Text>
        <Text size="xs" c="dimmed" lh={1.2}>
          {subtitle}
        </Text>
      </Stack>
    </Group>
  )
}
