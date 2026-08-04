import type { Mode } from '../App'

interface HeaderProps {
  mode: Mode
  onToggle: () => void
}

export default function Header({ mode, onToggle }: HeaderProps) {
  return (
    <header className="app-header">
      <div className="header-brand">
        <span className="brand-dot" />
        <span className="brand-title">MCP Agent</span>
      </div>
      <button className="mode-toggle" onClick={onToggle} aria-label="Toggle colour mode">
        {mode === 'light' ? '🌙 Dark' : '☀️ Light'}
      </button>
    </header>
  )
}
