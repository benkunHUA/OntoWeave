import {
  ActionIcon,
  Box,
  Group,
  Modal,
  Stack,
  Text,
  ThemeIcon,
  Title,
} from '@mantine/core'
import { X } from 'lucide-react'
import type { ReactNode } from 'react'
import { useEscapeClose } from '../../hooks/useEscapeClose'

export type DialogIconTone = 'default' | 'danger'

interface DialogShellProps {
  icon: ReactNode
  iconTone?: DialogIconTone
  title: string
  subtitle: string
  size?: 'xs' | 'sm' | 'md' | 'lg' | 'xl'
  /** 右上角关闭按钮与遮罩层共用的收起动作。 */
  onClose: () => void
  /** 底部按钮区，由调用方决定按钮语义。 */
  footer: ReactNode
  children: ReactNode
}

/** 弹窗外壳：遮罩、标题栏（图标 + 标题 + 副标题 + 关闭）和底部栏。 */
export function DialogShell({
  icon,
  iconTone = 'default',
  title,
  subtitle,
  size = 'md',
  onClose,
  footer,
  children,
}: DialogShellProps) {
  useEscapeClose(true, onClose)

  return (
    <Modal
      opened
      onClose={onClose}
      closeOnEscape={false}
      aria-label={title}
      centered
      size={size}
      padding={0}
      radius="lg"
      withCloseButton={false}
      closeOnClickOutside
      overlayProps={{ backgroundOpacity: 0.68, blur: 4 }}
      transitionProps={{ transition: 'fade', duration: 120 }}
      classNames={{
        content: 'app-dialog',
        body: 'app-dialog__body',
      }}
    >
      <Group justify="space-between" align="flex-start" gap="md" p="lg" className="app-dialog__header">
        <Group align="flex-start" gap="sm" wrap="nowrap">
          <ThemeIcon
            size={38}
            radius="md"
            variant="light"
            color={iconTone === 'danger' ? 'red' : 'brand'}
            aria-hidden="true"
          >
            {icon}
          </ThemeIcon>
          <Stack gap={3}>
            <Title order={3} size="h4">
              {title}
            </Title>
            <Text size="xs" c="dimmed">
              {subtitle}
            </Text>
          </Stack>
        </Group>
        <ActionIcon type="button" variant="subtle" color="gray" aria-label="关闭" onClick={onClose}>
          <X size={16} />
        </ActionIcon>
      </Group>

      <Box>{children}</Box>

      <Group justify="flex-end" gap="sm" p="md" className="app-dialog__footer">
        {footer}
      </Group>
    </Modal>
  )
}
