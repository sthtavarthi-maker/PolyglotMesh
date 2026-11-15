import { useEffect } from 'react'

const SITE_NAME = 'PolyglotMesh'

/**
 * Keeps the browser tab title in sync with the active route.
 * Call it once per page component.
 */
export function useDocumentTitle(title?: string) {
  useEffect(() => {
    document.title = title ? `${title} · ${SITE_NAME}` : SITE_NAME
  }, [title])
}
