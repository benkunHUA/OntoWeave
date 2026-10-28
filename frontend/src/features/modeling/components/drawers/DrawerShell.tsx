import { ActionIcon, Button, Group, Paper, Stack, Text, ThemeIcon } from '@mantine/core'
import { CircleCheck, Save, X } from 'lucide-react'
import type { ReactNode } from 'react'
import type { Status } from '../../../../shared/types'
import type { DrawerSubmitOptions } from '../../lib/drawerSubmit'
import { STATUS_LABEL } from '../../lib/status'

interface DrawerShellProps {
  ariaLabel: string
  icon: ReactNode
  title: string
  status: Status
  /** 额外的样式类：抽屉宽度等按元素类型微调。 */
  className?: string
  isSaving: boolean
  canSave: boolean
  error: string | null
  hint: string | null
  onClose: () => void
  onSubmit: (options: DrawerSubmitOptions) => void
  /** 额外的次要动作，比如「删除关系」，排在左下角。 */
  extraActions?: ReactNode
  children: ReactNode
}

/** 抽屉的共享外壳：头部（图标/标题/状态/关闭）+ 表单 + 底部动作条。 */
export function DrawerShell({
  ariaLabel,
  icon,
  title,
  status,
  className,
  isSaving,
  canSave,
  error,
  hint,
  onClose,
  onSubmit,
  extraActions,
  children,
}: DrawerShellProps) {
  return (
    <Paper
      component="aside"
      className={`drawer${className ? ` ${className}` : ''}`}
      aria-label={ariaLabel}
      withBorder={false}
      radius={0}
    >
      <Group className="drawer__header" gap={10} wrap="nowrap">
        <ThemeIcon className="drawer__icon" size={34} radius="md" variant="light" color="brand">
          {icon}
        </ThemeIcon>
        <Stack className="drawer__heading" gap={4}>
          <Text component="strong" size="sm">{title}</Text>
          <span className={`status-dot status-dot--${status}`}>{STATUS_LABEL[status]}</span>
        </Stack>
        <ActionIcon
          type="button"
          variant="subtle"
          color="gray"
          aria-label="关闭详情"
          onClick={onClose}
        >
          <X size={16} />
        </ActionIcon>
      </Group>

      <form
        className="drawer__form"
        onSubmit={(event) => {
          event.preventDefault()
          if (canSave) onSubmit({ confirm: false })
        }}
      >
        {children}

        {error ? <Text className="drawer__error" size="xs">{error}</Text> : null}
        {hint ? <Text className="drawer__hint" size="xs">{hint}</Text> : null}

        <Group className="drawer__footer" gap="xs" wrap="wrap">
          {extraActions}
          <Button type="button" variant="default" onClick={onClose}>
            关闭
          </Button>
          <Button
            type="submit"
            variant="default"
            disabled={!canSave}
            leftSection={<Save size={15} />}
          >
            保存
          </Button>
          <Button
            type="button"
            disabled={!canSave}
            loading={isSaving}
            leftSection={isSaving ? undefined : <CircleCheck size={15} />}
            onClick={() => onSubmit({ confirm: true })}
          >
            {isSaving ? '处理中' : '保存并确认'}
          </Button>
        </Group>
      </form>
    </Paper>
  )
}
