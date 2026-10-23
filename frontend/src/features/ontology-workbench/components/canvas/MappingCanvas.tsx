import {
  Background,
  BackgroundVariant,
  Controls,
  MarkerType,
  ReactFlow,
  ReactFlowProvider,
  type NodeTypes,
} from '@xyflow/react'
import { Database, Share2 } from 'lucide-react'
import { useMemo, useState } from 'react'
import type { ObjectMapping, OntologyResource } from '../../../../shared/types'
import { PROPERTY_DATATYPE_LABELS, supportsPrimaryKey } from '../../lib/mapping'
import { CANVAS_COPY } from './copy'
import { MappingConfirmDialog } from './MappingConfirmDialog'
import { MappingDatasetPicker } from './MappingDatasetPicker'
import { MappingEdge } from './MappingEdge'
import { MappingIssuePanel, MappingBanners } from './MappingMessages'
import { MappingNode } from './MappingNode'
import { MappingPreviewDrawer } from './MappingPreviewDrawer'
import { MappingToolbar } from './MappingToolbar'
import { buildLeftFields, buildMappingEdges, buildRightFields } from './mappingModel'
import type { MappingField, MappingFlowNode } from './types'
import { useMappingDraft } from './useMappingDraft'

const nodeTypes = { mapping: MappingNode } as NodeTypes
const edgeTypes = { mapping: MappingEdge }
const LEFT_NODE = 'ontology-attributes'
const RIGHT_NODE = 'dataset-columns'
const NODE_STYLE = { width: 300 } as const

interface MappingCanvasProps {
  mode: 'object' | 'relation'
  resource: OntologyResource
}

type PendingKeyChange = { kind: 'primary' | 'title'; value: string | null }

function MappingCanvasSurface({ mode, resource }: MappingCanvasProps) {
  const draft = useMappingDraft(mode, resource.id)
  const [pendingKey, setPendingKey] = useState<PendingKeyChange | null>(null)
  const { definition, columns, state, blocking, warnings, preview } = draft
  const objectMapping = state.value.mapping as ObjectMapping

  const leftFields = useMemo<MappingField[]>(() => {
    return buildLeftFields(mode, definition, state.value.mapping, blocking, warnings)
  }, [blocking, definition, mode, state.value.mapping, warnings])

  const rightFields = useMemo(
    () => buildRightFields(columns, state.value.mapping, blocking, warnings),
    [blocking, columns, state.value.mapping, warnings],
  )

  const nodes = useMemo<MappingFlowNode[]>(() => {
    const titleOptions = (definition?.properties ?? [])
      .filter((property) => !property.is_array)
      .map((property) => ({ id: property.id, label: `${property.name} · ${PROPERTY_DATATYPE_LABELS[property.datatype]}` }))
    const primaryOptions = titleOptions.filter((option) => {
      const property = definition?.properties.find((item) => item.id === option.id)
      return property ? supportsPrimaryKey(property) : false
    })
    return [
      {
        id: LEFT_NODE,
        type: 'mapping',
        position: { x: 0, y: 0 },
        draggable: false,
        style: NODE_STYLE,
        data: {
          side: 'left',
          title: definition?.name ?? resource.name,
          subtitle: mode === 'object' ? CANVAS_COPY.attributes : CANVAS_COPY.endpoints,
          fields: leftFields,
          keyControls: mode === 'object' ? {
            primaryValue: objectMapping.primary_key_attribute_id ?? '',
            titleValue: objectMapping.title_key_attribute_id ?? null,
            primaryOptions,
            titleOptions,
            onPrimaryChange: (value) => setPendingKey({ kind: 'primary', value }),
            onTitleChange: (value) => setPendingKey({ kind: 'title', value }),
          } : undefined,
        },
      },
      {
        id: RIGHT_NODE,
        type: 'mapping',
        position: { x: 370, y: 0 },
        draggable: false,
        style: NODE_STYLE,
        data: {
          side: 'right',
          title: draft.datasets.find((item) => item.kind === state.value.datasetKind && item.id === state.value.datasetId)?.name
            ?? CANVAS_COPY.columns,
          subtitle: CANVAS_COPY.columns,
          fields: rightFields,
        },
      },
    ]
  }, [definition, draft.datasets, leftFields, mode, objectMapping, resource.name, rightFields, state.value.datasetId, state.value.datasetKind])

  const edges = useMemo(
    () => buildMappingEdges(mode, state.value.mapping, definition, columns, draft.removeEdge),
    [columns, definition, draft.removeEdge, mode, state.value.mapping],
  )

  const busy = draft.isSaving || draft.isSuggesting || draft.isValidating || draft.isPreviewing || draft.isGenerating

  return (
    <section className="mapping-canvas" aria-label={mode === 'object' ? CANVAS_COPY.objectTitle : CANVAS_COPY.relationTitle}>
      <header className="mapping-canvas__head">
        <div>
          <span className="eyebrow">{CANVAS_COPY.eyebrow}</span>
          <h2>{mode === 'object' ? CANVAS_COPY.objectTitle : CANVAS_COPY.relationTitle}</h2>
          <p>{mode === 'object' ? CANVAS_COPY.objectHint : CANVAS_COPY.relationHint}</p>
        </div>
        <MappingDatasetPicker
          datasets={draft.datasets}
          selectedKind={state.value.datasetKind}
          selectedId={state.value.datasetId}
          isLoading={draft.isLoading || draft.isColumnsLoading}
          onSelect={draft.setDataset}
        />
      </header>

      <MappingBanners
        error={draft.error}
        conflict={draft.conflict}
        feedback={draft.feedback}
        onRetry={() => void draft.reload()}
        onDismissFeedback={draft.clearFeedback}
      />

      <MappingToolbar
        mode={mode}
        revision={state.value.revision}
        dirty={draft.dirty}
        blockingCount={blocking.length}
        warningCount={warnings.length}
        hasDataset={draft.hasDataset}
        canUndo={draft.canUndo}
        canRedo={draft.canRedo}
        canSave={draft.canSave}
        canGenerate={draft.canGenerate}
        isSaving={draft.isSaving}
        isSuggesting={draft.isSuggesting}
        isValidating={draft.isValidating}
        isPreviewing={draft.isPreviewing}
        isGenerating={draft.isGenerating}
        onUndo={draft.undo}
        onRedo={draft.redo}
        onSuggest={() => void draft.suggest()}
        onValidate={() => void draft.validate()}
        onPreview={() => void draft.previewMapping()}
        onSave={() => void draft.save()}
        onGenerate={() => void draft.generate()}
      />

      <div className="mapping-canvas__stage">
        {draft.isLoading ? (
          <div className="mapping-canvas__empty"><Share2 size={22} /><strong>{CANVAS_COPY.definitionLoading}</strong></div>
        ) : (
          <ReactFlow<MappingFlowNode>
            nodes={nodes}
            edges={edges}
            nodeTypes={nodeTypes}
            edgeTypes={edgeTypes}
            defaultEdgeOptions={{ markerEnd: { type: MarkerType.ArrowClosed, width: 14, height: 14 } }}
            onConnect={(connection) => draft.connect(connection.sourceHandle, connection.targetHandle)}
            onEdgesDelete={(deleted) => deleted.forEach((edge) => draft.removeEdge(edge.id))}
            onEdgeDoubleClick={(_, edge) => draft.removeEdge(edge.id)}
            nodesDraggable={false}
            nodesConnectable={!busy}
            edgesFocusable
            deleteKeyCode={['Backspace', 'Delete']}
            fitView
            fitViewOptions={{ padding: 0.18 }}
            minZoom={0.45}
            maxZoom={1.4}
            proOptions={{ hideAttribution: true }}
          >
            <Background variant={BackgroundVariant.Dots} gap={22} size={1} color="#343434" />
            <Controls showInteractive={false} />
          </ReactFlow>
        )}
        {!draft.isLoading && !draft.hasDataset ? (
          <div className="mapping-canvas__empty">
            <Database size={24} />
            <strong>{CANVAS_COPY.noDatasetTitle}</strong>
            <p>{CANVAS_COPY.noDatasetBody}</p>
          </div>
        ) : null}
        {!draft.isLoading && preview ? (
          <MappingPreviewDrawer preview={preview} onClose={draft.clearPreview} />
        ) : null}
      </div>

      <MappingIssuePanel blocking={blocking} warnings={warnings} />

      {pendingKey ? (
        <MappingConfirmDialog
          title={pendingKey.kind === 'primary' ? CANVAS_COPY.confirmPrimaryTitle : CANVAS_COPY.confirmTitleTitle}
          body={pendingKey.kind === 'primary' ? CANVAS_COPY.confirmPrimaryBody : CANVAS_COPY.confirmTitleBody}
          onCancel={() => setPendingKey(null)}
          onConfirm={() => {
            if (pendingKey.kind === 'primary') draft.setPrimaryKey(pendingKey.value ?? '')
            else draft.setTitleKey(pendingKey.value)
            setPendingKey(null)
          }}
        />
      ) : null}
    </section>
  )
}

export function MappingCanvas(props: MappingCanvasProps) {
  return (
    <ReactFlowProvider>
      <MappingCanvasSurface {...props} />
    </ReactFlowProvider>
  )
}
