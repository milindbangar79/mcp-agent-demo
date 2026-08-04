import type { Message } from '../types'

interface ChatMessageProps {
  message: Message
}

export default function ChatMessage({ message }: ChatMessageProps) {
  const isUser = message.role === 'user'
  return (
    <div className={`message-row ${isUser ? 'user' : 'assistant'}`}>
      <div className="message-label">{isUser ? 'You' : 'Agent'}</div>
      <div className="message-bubble">{message.content}</div>
    </div>
  )
}
