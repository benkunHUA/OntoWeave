import type { CSSProperties, PointerEvent, ReactNode } from 'react'
import { PaneResizer } from './PaneResizer'

interface WorkbenchPaneLayoutProps {
  pageClassName: string
  dividerClassName: string
  leftWidth: number
  rightWidth: number
  left: ReactNode
  center: ReactNode
  right: ReactNode
  dragging: boolean
  resizerLabel: string
  onStartResize: (event: PointerEvent<HTMLDivElement>) => void
  styleVars?: CSSProperties
}

/** 左列表、中对话、右工作区的共享三栏 DOM 骨架。 */
export function WorkbenchPaneLayout({
  pageClassName,
  dividerClassName,
  leftWidth,
  rightWidth,
  left,
  center,
  right,
  dragging,
  resizerLabel,
  onStartResize,
  styleVars,
}: WorkbenchPaneLayoutProps) {
  const layoutStyle = {
    ...styleVars,
    '--workbench-left-w': `${leftWidth}px`,
    '--workbench-right-w': `${rightWidth}px`,
  } as CSSProperties

  return (
    <main className={pageClassName} style={layoutStyle}>
      {left}
      <div className={dividerClassName} />
      {center}
      <PaneResizer
        dragging={dragging}
        ariaLabel={resizerLabel}
        onPointerDown={onStartResize}
      />
      {right}
    </main>
  )
}
