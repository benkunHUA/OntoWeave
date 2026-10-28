import { Paper, Stack, Text, UnstyledButton } from '@mantine/core'
import type { NodeCatalog } from '../../../shared/types'
import { NODE_DRAG_MIME } from '../lib/constants'
import { groupedNodeDefinitions } from '../lib/nodeCatalog'

interface NodePaletteProps {
  catalog: NodeCatalog | null
}

/** 节点目录完全由 /pipelines/node-catalog 驱动。 */
export function NodePalette({ catalog }: NodePaletteProps) {
  const groups = groupedNodeDefinitions(catalog)

  return (
    <Paper component="aside" className="pipeline-palette" aria-label="节点目录" withBorder radius="md">
      <Text className="pipeline-palette__head" size="xs" fw={700} c="dimmed" tt="uppercase">
        节点
      </Text>
      <Stack className="pipeline-palette__body" gap="md">
        {groups.map((group) => (
          <Stack component="section" key={group.category} className="pipeline-palette__group" gap={4}>
            <Text component="h3" size="xs" fw={700} c="dimmed">{group.label}</Text>
            <Stack gap={4}>
              {group.nodes.map((definition) => (
                <UnstyledButton
                  key={definition.type}
                  type="button"
                  className="pipeline-palette__item"
                  draggable
                  onDragStart={(event) => {
                    event.dataTransfer.setData(NODE_DRAG_MIME, definition.type)
                    event.dataTransfer.effectAllowed = 'move'
                  }}
                  title={`拖入画布：${definition.label}`}
                >
                  <Text component="span" size="xs">{definition.label}</Text>
                </UnstyledButton>
              ))}
            </Stack>
          </Stack>
        ))}
        {!groups.length ? <Text className="pipeline-palette__empty" size="xs" c="dimmed">节点目录不可用</Text> : null}
      </Stack>
    </Paper>
  )
}
