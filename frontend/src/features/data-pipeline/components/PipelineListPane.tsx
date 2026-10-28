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
import { House, Plus, Trash2, Workflow } from 'lucide-react'
import { useMemo, useState } from 'react'
import { Brand } from '../../../shared/components/Brand'
import { EmptyState } from '../../../shared/components/EmptyState'
import { SearchField } from '../../../shared/components/form/controls'
import type { Pipeline } from '../../../shared/types'
import {
  PIPELINE_DELETE_COPY,
  PIPELINE_EMPTY_COPY,
  PIPELINE_NEW_COPY,
  PIPELINE_STATUS_LABELS,
} from '../lib/constants'
import { DeletePipelineDialog } from './dialogs/DeletePipelineDialog'

interface PipelineListPaneProps {
  pipelines: Pipeline[]
  activeId: string | null
  isLoading: boolean
  error: string | null
  onCreate: () => Promise<Pipeline | null>
  onDelete: (pipeline: Pipeline) => Promise<boolean>
  onSelect: (pipeline: Pipeline) => void
  onHome: () => void
}

export function PipelineListPane({
  pipelines,
  activeId,
  isLoading,
  error,
  onCreate,
  onDelete,
  onSelect,
  onHome,
}: PipelineListPaneProps) {
  const [query, setQuery] = useState('')
  const [isCreating, setIsCreating] = useState(false)
  const [pendingDelete, setPendingDelete] = useState<Pipeline | null>(null)
  const [isDeleting, setIsDeleting] = useState(false)

  const filtered = useMemo(() => {
    const keyword = query.trim().toLowerCase()
    if (!keyword) return pipelines
    return pipelines.filter((pipeline) => pipeline.name.toLowerCase().includes(keyword))
  }, [pipelines, query])

  async function create() {
    if (isCreating) return
    setIsCreating(true)
    await onCreate()
    setIsCreating(false)
  }

  async function confirmDelete() {
    if (!pendingDelete || isDeleting) return
    setIsDeleting(true)
    const deleted = await onDelete(pendingDelete)
    setIsDeleting(false)
    if (deleted) setPendingDelete(null)
  }

  return (
    <aside className="pipeline-list-pane">
      <Group className="pipeline-list-pane__header" justify="space-between" wrap="nowrap">
        <Brand
          compact
          icon={<Workflow size={17} />}
          title="OntoWeave"
          subtitle="数据整理工作台"
        />
        <ActionIcon type="button" variant="subtle" color="gray" aria-label="返回首页" onClick={onHome}>
          <House size={17} />
        </ActionIcon>
      </Group>

      <Button
        type="button"
        className="pipeline-new-button"
        fullWidth
        leftSection={<Plus size={16} />}
        onClick={() => void create()}
        disabled={isCreating}
      >
        {isCreating ? PIPELINE_NEW_COPY.creating : PIPELINE_NEW_COPY.create}
      </Button>

      <SearchField
        className="pipeline-search"
        value={query}
        onChange={(event) => setQuery(event.target.value)}
        placeholder="搜索数据整理流程"
        aria-label="搜索数据整理流程"
      />

      <ScrollArea className="pipeline-list" type="auto" offsetScrollbars>
        <Stack component="nav" aria-label="数据整理流程列表" gap={4} p="xs">
          {filtered.map((pipeline) => {
            const active = pipeline.id === activeId
            return (
              <Group
                key={pipeline.id}
                className={`pipeline-list__item ${active ? 'is-active' : ''}`}
                gap={4}
                wrap="nowrap"
              >
                <UnstyledButton
                  type="button"
                  className="pipeline-list__select"
                  onClick={() => onSelect(pipeline)}
                  aria-current={active ? 'page' : undefined}
                >
                  <span className={`pipeline-list__status is-${pipeline.status}`} />
                  <Stack className="pipeline-list__body" gap={3} align="flex-start">
                    <Text className="pipeline-list__name" size="sm" fw={600}>{pipeline.name}</Text>
                    <Text className="pipeline-list__meta" size="xs" c="dimmed">
                      {pipeline.current_version_id ? '已发布版本可用' : '尚未发布'}
                    </Text>
                  </Stack>
                  <Badge color={pipeline.status === 'active' ? 'teal' : 'gray'} variant="light">
                    {PIPELINE_STATUS_LABELS[pipeline.status] ?? pipeline.status}
                  </Badge>
                </UnstyledButton>
                <ActionIcon
                  type="button"
                  className="pipeline-list__delete"
                  variant="subtle"
                  color="gray"
                  size="sm"
                  aria-label={`${PIPELINE_DELETE_COPY.action}：${pipeline.name}`}
                  onClick={() => setPendingDelete(pipeline)}
                >
                  <Trash2 size={14} />
                </ActionIcon>
              </Group>
            )
          })}
          {isLoading ? <EmptyState className="pipeline-list__empty" title="正在读取流程…" /> : null}
          {!isLoading && !filtered.length ? (
            <EmptyState
              className="pipeline-list__empty"
              title={PIPELINE_EMPTY_COPY.list}
              body={PIPELINE_EMPTY_COPY.listHint}
            />
          ) : null}
          {error ? <Text className="pipeline-list__error" size="xs">{error}</Text> : null}
        </Stack>
      </ScrollArea>
      {pendingDelete ? (
        <DeletePipelineDialog
          name={pendingDelete.name}
          isDeleting={isDeleting}
          onCancel={() => setPendingDelete(null)}
          onConfirm={() => void confirmDelete()}
        />
      ) : null}
    </aside>
  )
}
