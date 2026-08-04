export interface Message {
  id: string
  role: 'user' | 'assistant'
  content: string
  timestamp: Date
}

export interface DiscoveredTool {
  server: string
  name: string
  description: string
}
