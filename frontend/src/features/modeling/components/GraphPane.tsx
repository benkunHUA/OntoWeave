import { useModeling } from '../context/modelingContext'
import { useGraphPublication } from '../hooks/useGraphPublication'
import { publicationBlockedReason } from '../lib/publication'
import { ActionDrawer } from './action/ActionDrawer'
import { GraphPublicationControl } from './GraphPublicationControl'
import { NodeDrawer } from './NodeDrawer'
import { OntologyCanvas } from './OntologyCanvas'
import { RelationDrawer } from './RelationDrawer'

/** 右侧画布栏：图 + 按选中项挂上来的对象 / 关系 / 动作抽屉（一次只开一个）。 */
export function GraphPane() {
  const {
    workspace,
    selection,
    nodeDrawer,
    relationDrawer,
    actionDrawer,
    pendingDeleteIds,
    toast,
  } = useModeling()
  const { selectedNode, selectedRelation, relationEndpoints, foreignKeyCandidates } = selection
  const { selectedActionId, selectedAction } = selection
  const actionDetail = actionDrawer.detail
  const graphId = workspace.conversation?.resource_id
  const publication = useGraphPublication(graphId, toast.setMessage)
  const blockedPublishReason = publicationBlockedReason(workspace.snapshot, workspace.changeset)

  // 标删提示只数这个对象自己待删的字段，别人图上删的东西不在这里出现。
  const nodePendingDeletes = selectedNode
    ? new Set(
        selectedNode.properties
          .filter((property) => pendingDeleteIds.has(property.id))
          .map((property) => property.id),
      )
    : pendingDeleteIds

  return (
    <section className="graph-pane">
      <OntologyCanvas
        snapshot={workspace.snapshot}
        viewSpec={workspace.viewSpec}
        changeset={workspace.changeset}
        selectedNodeId={selection.selectedNodeId}
        selectedRelationId={selection.selectedRelationId}
        selectedActionId={selection.selectedActionId}
        onSelectNode={selection.selectNode}
        onSelectRelation={selection.selectRelation}
        onSelectAction={selection.selectAction}
        onLayoutChange={(kind) =>
          workspace.updateViewSpec({
            ...workspace.viewSpec,
            layout: { kind, options: {} },
            nodes: {},
          })
        }
        onMoveNode={(nodeId, x, y) =>
          workspace.updateViewSpec(
            {
              ...workspace.viewSpec,
              nodes: { ...(workspace.viewSpec.nodes ?? {}), [nodeId]: { x, y } },
            },
            500,
          )
        }
        onSaveView={() => workspace.updateViewSpec(workspace.viewSpec)}
        toolbarExtra={
          graphId ? (
            <GraphPublicationControl
              publication={publication.publication}
              isLoading={publication.isLoading}
              hasError={publication.hasError}
              isPublishing={publication.isPublishing}
              blockedReason={blockedPublishReason}
              onPublish={publication.publish}
            />
          ) : null
        }
      />

      {selectedNode ? (
        <NodeDrawer
          key={`${selectedNode.id}:${nodeDrawer.savedToken}`}
          node={selectedNode}
          pendingDeleteIds={nodePendingDeletes}
          isSaving={nodeDrawer.isSaving}
          error={nodeDrawer.error}
          onClose={selection.closeDrawer}
          onSubmit={(draft, options) => void nodeDrawer.save(draft, options)}
        />
      ) : null}

      {selectedAction && selectedActionId && actionDetail ? (
        <ActionDrawer
          key={`${selectedActionId}:${actionDrawer.savedToken}`}
          detail={actionDetail}
          snapshot={workspace.snapshot}
          pendingDeleteIds={pendingDeleteIds}
          isSaving={actionDrawer.isSaving}
          error={actionDrawer.error}
          onClose={selection.closeActionDrawer}
          onSubmit={(draft, options) => void actionDrawer.save(draft, options)}
        />
      ) : null}

      {selectedRelation && relationEndpoints ? (
        <RelationDrawer
          key={`${selectedRelation.id}:${relationDrawer.savedToken}`}
          relation={selectedRelation}
          sourceName={relationEndpoints.source}
          targetName={relationEndpoints.target}
          foreignKeyCandidates={foreignKeyCandidates}
          isSaving={relationDrawer.isSaving}
          error={relationDrawer.error}
          onClose={selection.closeRelationDrawer}
          onSubmit={(draft, options) => void relationDrawer.save(draft, options)}
          onDelete={() => void relationDrawer.remove()}
        />
      ) : null}
    </section>
  )
}
