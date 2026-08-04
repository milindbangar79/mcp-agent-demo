import { useEffect, useState } from 'react'
import { SaltProvider } from '@salt-ds/core'
import Header from './components/Header'
import ToolsPanel from './components/ToolsPanel'
import ChatPanel from './components/ChatPanel'

export type Mode = 'light' | 'dark'

export default function App() {
  const [mode, setMode] = useState<Mode>(() => {
    const stored = localStorage.getItem('mcp-agent-mode')
    return stored === 'dark' ? 'dark' : 'light'
  })

  useEffect(() => {
    document.body.setAttribute('data-mode', mode)
    localStorage.setItem('mcp-agent-mode', mode)
  }, [mode])

  function toggleMode() {
    setMode(m => (m === 'light' ? 'dark' : 'light'))
  }

  return (
    <SaltProvider mode={mode}>
      <div className="app-layout">
        <Header mode={mode} onToggle={toggleMode} />
        <main className="app-main">
          <ToolsPanel />
          <ChatPanel />
        </main>
      </div>
    </SaltProvider>
  )
}
