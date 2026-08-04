import { useEffect, useRef, useState } from 'react'
import { sendPrompt } from '../api/agentApi'
import ChatMessage from './ChatMessage'
import type { Message } from '../types'

export default function ChatPanel() {
  const [messages, setMessages] = useState<Message[]>([])
  const [prompt, setPrompt] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const bottomRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, loading])

  async function handleSend() {
    const text = prompt.trim()
    if (!text || loading) return

    const userMsg: Message = {
      id: crypto.randomUUID(),
      role: 'user',
      content: text,
      timestamp: new Date(),
    }
    setMessages(prev => [...prev, userMsg])
    setPrompt('')
    setError(null)
    setLoading(true)

    try {
      const answer = await sendPrompt(text)
      const agentMsg: Message = {
        id: crypto.randomUUID(),
        role: 'assistant',
        content: answer,
        timestamp: new Date(),
      }
      setMessages(prev => [...prev, agentMsg])
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Something went wrong')
    } finally {
      setLoading(false)
    }
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSend()
    }
  }

  return (
    <div className="chat-panel">
      <div className="chat-messages">
        {messages.length === 0 && !loading && (
          <div className="chat-empty">
            <div className="chat-empty-title">Ask me anything</div>
            <div className="chat-empty-hint">
              Try: "Calculate simple interest on 10000 at 7.5% for 3 years"
            </div>
          </div>
        )}

        {messages.map(msg => (
          <ChatMessage key={msg.id} message={msg} />
        ))}

        {loading && (
          <div className="message-row assistant">
            <div className="message-label">Agent</div>
            <div className="thinking-bubble">
              <div className="thinking-dots">
                <span />
                <span />
                <span />
              </div>
              Thinking…
            </div>
          </div>
        )}

        <div ref={bottomRef} />
      </div>

      <div className="chat-input-area">
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 4 }}>
          <textarea
            className="chat-textarea"
            value={prompt}
            onChange={e => setPrompt(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Type a prompt… (Enter to send, Shift+Enter for new line)"
            rows={2}
            disabled={loading}
          />
          {error && <div className="chat-error">⚠ {error}</div>}
          <div className="input-hint">Enter to send · Shift+Enter for new line</div>
        </div>
        <button
          className="send-btn"
          onClick={handleSend}
          disabled={!prompt.trim() || loading}
          aria-label="Send prompt"
        >
          Send →
        </button>
      </div>
    </div>
  )
}
