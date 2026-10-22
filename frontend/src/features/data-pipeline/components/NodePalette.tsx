import type { NodeCatalog } from '../../../shared/types'
import { NODE_DRAG_MIME } from '../lib/constants'
import { groupedNodeDefinitions } from '../lib/nodeCatalog'

interface NodePaletteProps {
  catalog: NodeCatalog | null
}

/** 节点目录完全由 /pipelines/node-catalog 驱动。 */
export function NodePalette({ catalog }: NodePaletteProps) {
  const groups = groupedNodeDefinitions(catalog)

  return (
    <aside className="pipeline-palette" aria-label="节点目录">
      <header className="pipeline-palette__head">
        <span>节点</span>
      </header>
      <div className="pipeline-palette__body">
        {groups.map((group) => (
          <section key={group.category} className="pipeline-palette__group">
            <h3>{group.label}</h3>
            {group.nodes.map((definition) => (
              <button
                key={definition.type}
                type="button"
                className="pipeline-palette__item"
                draggable
                onDragStart={(event) => {
                  event.dataTransfer.setData(NODE_DRAG_MIME, definition.type)
                  event.dataTransfer.effectAllowed = 'move'
                }}
                title={`拖入画布：${definition.label}`}
              >
                <span>{definition.label}</span>
              </button>
            ))}
          </section>
        ))}
        {!groups.length ? <p className="pipeline-palette__empty">节点目录不可用</p> : null}
      </div>
    </aside>
  )
}
