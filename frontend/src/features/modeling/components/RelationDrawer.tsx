import { ActionIcon } from '@mantine/core'
import { ArrowRightLeft, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { NativeSelectField, TextField } from '../../../shared/components/form/controls'
import type { GraphRelation } from '../../../shared/types'
import {
  CARDINALITY_OPTIONS,
  draftFromRelation,
  foreignKeyAllowed,
  isRelationDraftDirty,
  relationDraftValidationError,
  type ForeignKeyCandidate,
  type RelationDraft,
} from '../lib/relationDetails'
import type { DrawerSubmitOptions } from '../lib/drawerSubmit'
import { DrawerShell } from './drawers/DrawerShell'

interface RelationDrawerProps {
  relation: GraphRelation
  sourceName: string
  targetName: string
  foreignKeyCandidates: ForeignKeyCandidate[]
  isSaving: boolean
  error: string | null
  onClose: () => void
  onSubmit: (draft: RelationDraft, options: DrawerSubmitOptions) => void
  onDelete: () => void
}

/**
 * 关系详情抽屉：改关系名、基数与两端可读名，或把关系标记删除。
 * 和 NodeDrawer 一样只产出草稿，保存交给父组件。
 */
export function RelationDrawer({
  relation,
  sourceName,
  targetName,
  foreignKeyCandidates,
  isSaving,
  error,
  onClose,
  onSubmit,
  onDelete,
}: RelationDrawerProps) {
  const [draft, setDraft] = useState<RelationDraft>(() => draftFromRelation(relation))

  const validationError = relationDraftValidationError(draft)
  const dirty = isRelationDraftDirty(relation, draft)
  const canSave = dirty && !validationError && !isSaving

  return (
    <DrawerShell
      ariaLabel="关系详情"
      icon={<ArrowRightLeft size={18} />}
      title={relation.name}
      status={relation.status}
      isSaving={isSaving}
      canSave={canSave}
      error={error}
      hint={validationError}
      onClose={onClose}
      onSubmit={(options) => onSubmit(draft, options)}
      extraActions={
        <ActionIcon
          type="button"
          variant="subtle"
          color="red"
          className="drawer__danger"
          aria-label="删除关系"
          title="删除关系"
          disabled={isSaving}
          onClick={onDelete}
        >
          <Trash2 size={16} />
        </ActionIcon>
      }
    >
      <div className="drawer__endpoints">
        <div className="drawer__endpoint">
          <span>源端</span>
          <strong>{sourceName}</strong>
        </div>
        <div className="drawer__endpoint">
          <span>目标端</span>
          <strong>{targetName}</strong>
        </div>
      </div>

      <TextField
        label="关系名"
        value={draft.name}
        placeholder="关系名"
        onChange={(event) => setDraft((current) => ({ ...current, name: event.target.value }))}
      />

      <NativeSelectField
        label="基数"
        value={draft.cardinality}
        data={CARDINALITY_OPTIONS.map((option) => ({
          value: option.value,
          label: `${option.label}（${option.mark}）`,
        }))}
        onChange={(event) => {
          const cardinality = event.target.value
          setDraft((current) => ({
            ...current,
            cardinality,
            // 多对多不用外键，切过去时清空，避免留下非法组合。
            foreignKeyPropertyId: foreignKeyAllowed(cardinality)
              ? current.foreignKeyPropertyId
              : '',
          }))
        }}
      />

      {foreignKeyAllowed(draft.cardinality) ? (
        <NativeSelectField
          label="外键属性"
          description="承载这条链接的属性，存的是对端主键的值。"
          value={draft.foreignKeyPropertyId}
          data={[
            { value: '', label: '不绑定' },
            ...foreignKeyCandidates.map((candidate) => ({
              value: candidate.value,
              label: candidate.label,
            })),
          ]}
          onChange={(event) =>
            setDraft((current) => ({ ...current, foreignKeyPropertyId: event.target.value }))
          }
        />
      ) : null}

      <TextField
        label="正向可读名"
        value={draft.forwardLabel}
        placeholder={`默认沿用「${draft.name || '关系名'}」`}
        onChange={(event) =>
          setDraft((current) => ({ ...current, forwardLabel: event.target.value }))
        }
      />

      <TextField
        label="反向可读名"
        value={draft.reverseLabel}
        placeholder={`默认沿用「${draft.name || '关系名'}」`}
        onChange={(event) =>
          setDraft((current) => ({ ...current, reverseLabel: event.target.value }))
        }
      />
    </DrawerShell>
  )
}
