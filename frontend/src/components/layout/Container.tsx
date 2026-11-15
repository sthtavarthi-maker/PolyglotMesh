import type { ReactNode } from 'react'

type ContainerProps = {
  children: ReactNode
  className?: string
}

/**
 * Consistent horizontal rhythm and maximum line length for every page section.
 * Width caps at 72rem so the workspace can opt out later by providing its own
 * wrapper instead of using this component.
 */
export function Container({ children, className = '' }: ContainerProps) {
  return (
    <div className={`mx-auto w-full max-w-6xl px-4 sm:px-6 lg:px-8 ${className}`}>
      {children}
    </div>
  )
}
