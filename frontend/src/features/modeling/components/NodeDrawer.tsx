import { Box, Plus } from 'lucide-react'
import { useState } from 'react'
import type { GraphNode } from '../../../shared/types'
import type { DrawerSubmitOptions } from '../lib/drawerSubmit'
import {
  draftFromNode,
  draftValidationError,
  isDraftDirty,
  newPropertyDraft,
  type NodeDraft,
  type PropertyDraft,
} from '../lib/nodeDetails'
import { DrawerShell } from './drawers/DrawerShell'
import { PropertyFieldRow } from './PropertyFieldRow'

interface NodeDrawerProps {
  node: GraphNode
  /** 已标记删除、待确认的字段：不再显示成可编辑行。 */
  pendingDeleteIds: ReadonlySet<string>
  isSaving: boolean
  error: string | null
  onClose: () => void
  onSubmit: (draft: NodeDraft, options: DrawerSubmitOptions) => void
}

/**
 * 对象详情抽屉：展示并编辑名称、描述与字段，字段可以改、增、删。
 * 保存时把草稿交给父组件去比对、提交，抽屉本身不直接调接口。
 */
export function NodeDrawer({
  node,
  pendingDeleteIds,
  isSaving,
  error,
  onClose,
  onSubmit,
}: NodeDrawerProps) {
  const [draft, setDraft] = useState<NodeDraft>(() => draftFromNode(node, pendingDeleteIds))
  const requiredById = new Map(node.properties.map((item) => [item.id, item.required]))

  const validationError = draftValidationError(draft)
  const dirty = isDraftDirty(node, draft)
  const canSave = dirty && !validationError && !isSaving

  function updateProperty(key: string, patch: Partial<PropertyDraft>) {
    setDraft((current) => ({
      ...current,
      properties: current.properties.map((property) => {
        if (property.key === key) return { ...property, ...patch }
        // 主键 / 标题键在一个对象类型里各只能有一个，选一个就清掉别的。
        if (patch.isPrimaryKey) return { ...property, isPrimaryKey: false }
        if (patch.isTitleKey) return { ...property, isTitleKey: false }
        return property
      }),
    }))
  }

  function addProperty() {
    setDraft((current) => ({
      ...current,
      properties: [...current.properties, newPropertyDraft()],
    }))
  }

  function removeProperty(key: string) {
    setDraft((current) => {
      const removed = current.properties.find((property) => property.key === key)
      return {
        ...current,
        properties: current.properties.filter((property) => property.key !== key),
        // 只有已存在的字段才需要记一笔删除；新增但没保存的直接丢掉即可。
        removedIds:
          removed && removed.id ? [...current.removedIds, removed.id] : current.removedIds,
      }
    })
  }

  return (
    <DrawerShell
      ariaLabel="对象详情"
      icon={<Box size={18} />}
      title={node.name}
      status={node.status}
      isSaving={isSaving}
      canSave={canSave}
      error={error}
      hint={validationError}
      onClose={onClose}
      onSubmit={(options) => onSubmit(draft, options)}
    >
      <label className="drawer__field">
        <span>名称</span>
        <input
          value={draft.name}
          placeholder="对象名称"
          onChange={(event) => setDraft((current) => ({ ...current, name: event.target.value }))}
        />
      </label>

      <label className="drawer__field">
        <span>描述</span>
        <textarea
          value={draft.description}
          rows={3}
          placeholder="这个对象代表什么？"
          onChange={(event) =>
            setDraft((current) => ({ ...current, description: event.target.value }))
          }
        />
      </label>

      <section className="drawer__fields">
        <div className="drawer__fields-head">
          <h4>
            字段 <em>{draft.properties.length}</em>
          </h4>
          <button type="button" className="text-button" onClick={addProperty}>
            <Plus size={14} />
            新增字段
          </button>
        </div>
        {pendingDeleteIds.size ? (
          <p className="drawer__pending">{pendingDeleteIds.size} 个字段将在确认改动后删除</p>
        ) : null}
        {draft.properties.length ? (
          draft.properties.map((property) => (
            <PropertyFieldRow
              key={property.key}
              property={property}
              required={requiredById.get(property.id ?? '') ?? false}
              onChange={updateProperty}
              onRemove={removeProperty}
            />
          ))
        ) : (
          <p className="drawer__empty">这个对象还没有字段</p>
        )}
      </section>
    </DrawerShell>
  )
}
