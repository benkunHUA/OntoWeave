import type { ReactNode } from 'react'

interface TabPlaceholderProps {
  icon: ReactNode
  title: string
  body: string
  className?: string
  aside?: ReactNode
}

export function TabPlaceholder({ icon, title, body, className = '', aside }: TabPlaceholderProps) {
  return (
    <section className={`ontology-tab-panel ${className}`.trim()}>
      <span className="ontology-tab-panel__icon">{icon}</span>
      <h2>{title}</h2>
      <p>{body}</p>
      {aside ? <div className="ontology-tab-panel__aside">{aside}</div> : null}
    </section>
  )
}
