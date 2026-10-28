import { Group, Stack, Text } from '@mantine/core'
import type { InterruptDetail } from '../../../shared/types'

/** 确认卡片里的候选详情（名称、描述、状态等），按 label/value 逐行展示。 */
export function InterruptDetails({ details }: { details: InterruptDetail[] }) {
  if (details.length === 0) return null
  return (
    <Stack className="interrupt-details" gap={4}>
      {details.map((detail, index) => (
        <Group className="interrupt-details__row" key={index} gap={10} wrap="nowrap" align="flex-start">
          <Text component="span" className="interrupt-details__label" size="xs">{detail.label}</Text>
          <Text component="span" className="interrupt-details__value" size="xs">{detail.value}</Text>
        </Group>
      ))}
    </Stack>
  )
}
