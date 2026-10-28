import {
  ActionIcon,
  Badge,
  Button,
  Group,
  ScrollArea,
  Stack,
  Text,
  UnstyledButton,
} from '@mantine/core'
import { ChevronRight, Database, House, Plus } from 'lucide-react'
import { useMemo, useState } from 'react'
import { Brand } from '../../../shared/components/Brand'
import { EmptyState } from '../../../shared/components/EmptyState'
import { SearchField } from '../../../shared/components/form/controls'
import type { DataConnection } from '../../../shared/types'
import { CONNECTION_STATUS_LABELS, RUN_STATUS_LABELS, TEST_STATUS_LABELS } from '../lib/labels'

interface ConnectionListPaneProps {
  connections: DataConnection[]
  activeId: string | null
  isCreating: boolean
  onSelect: (connection: DataConnection) => void
  onCreate: () => void
  onHome: () => void
}

export function ConnectionListPane({
  connections,
  activeId,
  isCreating,
  onSelect,
  onCreate,
  onHome,
}: ConnectionListPaneProps) {
  const [query, setQuery] = useState('')
  const filtered = useMemo(() => {
    const keyword = query.trim().toLowerCase()
    if (!keyword) return connections
    return connections.filter((connection) => connection.name.toLowerCase().includes(keyword))
  }, [connections, query])

  return (
    <aside className="connection-list-pane">
      <Group className="connection-list-pane__header" justify="space-between" wrap="nowrap">
        <Brand
          compact
          icon={<Database size={17} />}
          title="OntoWeave"
          subtitle="数据连接工作台"
        />
        <ActionIcon type="button" variant="subtle" color="gray" aria-label="返回首页" onClick={onHome}>
          <House size={17} />
        </ActionIcon>
      </Group>

      <Button
        type="button"
        className="new-connection-button"
        fullWidth
        leftSection={<Plus size={16} />}
        onClick={onCreate}
        disabled={isCreating}
      >
        {isCreating ? '正在创建' : '新建数据连接'}
      </Button>

      <SearchField
        className="connection-search"
        value={query}
        onChange={(event) => setQuery(event.target.value)}
        placeholder="搜索数据连接"
        aria-label="搜索数据连接"
      />

      <ScrollArea className="connection-list" type="auto" offsetScrollbars>
        <Stack component="nav" aria-label="数据连接列表" gap={4} p="xs">
          {filtered.map((connection) => {
            const isActive = connection.id === activeId
            return (
              <UnstyledButton
                type="button"
                key={connection.id}
                className={`connection-list__item ${isActive ? 'is-active' : ''}`}
                onClick={() => onSelect(connection)}
              >
                <span className={`connection-list__status is-${connection.status}`} />
                <Stack className="connection-list__body" gap={3} align="flex-start">
                  <Text className="connection-list__name" size="sm" fw={600}>{connection.name}</Text>
                  <Text className="connection-list__meta" size="xs" c="dimmed">
                    {connection.kind.toUpperCase()} · {TEST_STATUS_LABELS[connection.last_test_status]}
                  </Text>
                  <Text className="connection-list__meta" size="xs" c="dimmed">
                    最近同步：{connection.last_sync_status
                      ? RUN_STATUS_LABELS[connection.last_sync_status]
                      : '暂无'}
                  </Text>
                </Stack>
                <Badge color={connection.status === 'active' ? 'teal' : 'gray'} variant="light">
                  {CONNECTION_STATUS_LABELS[connection.status]}
                </Badge>
                <ChevronRight size={14} />
              </UnstyledButton>
            )
          })}
          {!filtered.length ? (
            <EmptyState
              className="connection-list__empty"
              title={connections.length ? '没有匹配的连接' : '还没有数据连接'}
            />
          ) : null}
        </Stack>
      </ScrollArea>
    </aside>
  )
}
