import type { ReactNode } from 'react'

type PaneHeaderProps = {
  title: string
  aside?: ReactNode
}

export function PaneHeader({ title, aside }: PaneHeaderProps) {
  return (
    <div className="border-line/80 bg-raised/50 flex items-center justify-between gap-3 border-b px-3 py-2">
      <h2 className="text-muted font-mono text-xs">{title}</h2>
      {aside}
    </div>
  )
}
