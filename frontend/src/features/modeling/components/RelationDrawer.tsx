import { ArrowRightLeft, Trash2 } from 'lucide-react'
import { useState } from 'react'
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
import { DrawerShell, type DrawerSubmitOptions } from './drawers/DrawerShell'

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
        <button
          type="button"
          className="icon-button drawer__danger"
          aria-label="删除关系"
          title="删除关系"
          disabled={isSaving}
          onClick={onDelete}
        >
          <Trash2 size={16} />
        </button>
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

      <label className="drawer__field">
        <span>关系名</span>
        <input
          value={draft.name}
          placeholder="关系名"
          onChange={(event) => setDraft((current) => ({ ...current, name: event.target.value }))}
        />
      </label>

      <label className="drawer__field">
        <span>基数</span>
        <select
          value={draft.cardinality}
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
        >
          {CARDINALITY_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}（{option.mark}）
            </option>
          ))}
        </select>
      </label>

      {foreignKeyAllowed(draft.cardinality) ? (
        <label className="drawer__field">
          <span>外键属性</span>
          <select
            value={draft.foreignKeyPropertyId}
            onChange={(event) =>
              setDraft((current) => ({ ...current, foreignKeyPropertyId: event.target.value }))
            }
          >
            <option value="">不绑定</option>
            {foreignKeyCandidates.map((candidate) => (
              <option key={candidate.value} value={candidate.value}>
                {candidate.label}
              </option>
            ))}
          </select>
          <small className="drawer__hint">承载这条链接的属性，存的是对端主键的值。</small>
        </label>
      ) : null}

      <label className="drawer__field">
        <span>正向可读名</span>
        <input
          value={draft.forwardLabel}
          placeholder={`默认沿用「${draft.name || '关系名'}」`}
          onChange={(event) =>
            setDraft((current) => ({ ...current, forwardLabel: event.target.value }))
          }
        />
      </label>

      <label className="drawer__field">
        <span>反向可读名</span>
        <input
          value={draft.reverseLabel}
          placeholder={`默认沿用「${draft.name || '关系名'}」`}
          onChange={(event) =>
            setDraft((current) => ({ ...current, reverseLabel: event.target.value }))
          }
        />
      </label>
    </DrawerShell>
  )
}
