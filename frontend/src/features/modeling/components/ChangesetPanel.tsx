import { useState } from 'react'
import { useModeling } from '../context/modelingContext'
import { ChangesetBar } from './ChangesetBar'

/** 待确认改动条：两个出口（撤销 / 确认）都在这里收口，成功后工作区自动重取。 */
export function ChangesetPanel() {
  const { workspace, opSummary, hasPendingChanges, toast } = useModeling()
  const [isConfirming, setIsConfirming] = useState(false)

  if (!hasPendingChanges) return null

  async function confirm() {
    setIsConfirming(true)
    try {
      await workspace.confirm()
    } catch (reason) {
      toast.report(reason, '确认失败')
    } finally {
      setIsConfirming(false)
    }
  }

  async function reject() {
    try {
      await workspace.reject()
    } catch (reason) {
      toast.report(reason, '撤销失败')
    }
  }

  return (
    <ChangesetBar
      summary={opSummary}
      isConfirming={isConfirming}
      onReject={() => void reject()}
      onConfirm={() => void confirm()}
    />
  )
}
