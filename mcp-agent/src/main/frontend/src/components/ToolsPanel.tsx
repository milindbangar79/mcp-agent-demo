import { useEffect, useState } from 'react'
import { Spinner } from '@salt-ds/core'
import { fetchTools } from '../api/agentApi'
import type { DiscoveredTool } from '../types'

export default function ToolsPanel() {
  const [tools, setTools] = useState<DiscoveredTool[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchTools()
      .then(setTools)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Failed to load tools'))
      .finally(() => setLoading(false))
  }, [])

  return (
    <aside className="tools-panel">
      <div className="tools-section-label">Available Tools</div>

      {loading && (
        <div className="tools-spinner">
          <Spinner size="small" aria-label="Loading tools" />
        </div>
      )}

      {error && <div className="tools-error">{error}</div>}

      {tools.map(tool => (
        <div key={`${tool.server}::${tool.name}`} className="tool-card">
          <span className="tool-server-badge">{tool.server}</span>
          <div className="tool-name">{tool.name}</div>
          <div className="tool-description">{tool.description}</div>
        </div>
      ))}
    </aside>
  )
}
