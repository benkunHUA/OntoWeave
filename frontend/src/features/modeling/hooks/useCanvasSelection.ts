import { useCallback, useMemo, useState } from 'react'
import type { GraphSnapshot } from '../../../shared/types'
import type { ForeignKeyCandidate } from '../lib/relationDetails'

/**
 * 画布上的选中项。一次只开一个抽屉：选中对象就收掉关系面板，点空白两个都关。
 * 派生数据（选中的元素、关系两端名字、外键候选）都从快照算，不进 state。
 */
export function useCanvasSelection(snapshot: GraphSnapshot | null) {
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null)
  const [selectedRelationId, setSelectedRelationId] = useState<string | null>(null)
  const [selectedActionId, setSelectedActionId] = useState<string | null>(null)

  const selectNode = useCallback((nodeId: string | null) => {
    setSelectedNodeId(nodeId)
    setSelectedRelationId(null)
    setSelectedActionId(null)
  }, [])

  const selectRelation = useCallback((relationId: string | null) => {
    setSelectedRelationId(relationId)
    setSelectedNodeId(null)
    setSelectedActionId(null)
  }, [])

  const selectAction = useCallback((actionId: string | null) => {
    setSelectedActionId(actionId)
    setSelectedNodeId(null)
    setSelectedRelationId(null)
  }, [])

  const closeDrawer = useCallback(() => setSelectedNodeId(null), [])
  const closeRelationDrawer = useCallback(() => setSelectedRelationId(null), [])
  const closeActionDrawer = useCallback(() => setSelectedActionId(null), [])

  const clear = useCallback(() => {
    setSelectedNodeId(null)
    setSelectedRelationId(null)
    setSelectedActionId(null)
  }, [])

  const selectedAction = useMemo(
    () => snapshot?.actions.find((action) => action.id === selectedActionId) ?? null,
    [snapshot, selectedActionId],
  )

  const selectedNode = useMemo(
    () => snapshot?.nodes.find((node) => node.id === selectedNodeId) ?? null,
    [snapshot, selectedNodeId],
  )

  const selectedRelation = useMemo(
    () => snapshot?.relations.find((relation) => relation.id === selectedRelationId) ?? null,
    [snapshot, selectedRelationId],
  )

  const relationEndpoints = useMemo(() => {
    if (!selectedRelation || !snapshot) return null
    const nameOf = (id: string) =>
      snapshot.nodes.find((node) => node.id === id)?.name ?? '未知对象'
    return {
      source: nameOf(selectedRelation.source_type_id),
      target: nameOf(selectedRelation.target_type_id),
    }
  }, [selectedRelation, snapshot])

  /** 外键属性候选：关系两端的对象类型各自持有的属性。 */
  const foreignKeyCandidates = useMemo<ForeignKeyCandidate[]>(() => {
    if (!selectedRelation || !snapshot) return []
    const candidates: ForeignKeyCandidate[] = []
    for (const typeId of [
      selectedRelation.source_type_id,
      selectedRelation.target_type_id,
    ]) {
      const node = snapshot.nodes.find((item) => item.id === typeId)
      if (!node) continue
      for (const property of node.properties) {
        candidates.push({ value: property.id, label: `${node.name} · ${property.name}` })
      }
    }
    return candidates
  }, [selectedRelation, snapshot])

  return {
    selectedNodeId,
    selectedRelationId,
    selectedActionId,
    selectedNode,
    selectedRelation,
    selectedAction,
    relationEndpoints,
    foreignKeyCandidates,
    selectNode,
    selectRelation,
    selectAction,
    closeDrawer,
    closeRelationDrawer,
    closeActionDrawer,
    clear,
  }
}
