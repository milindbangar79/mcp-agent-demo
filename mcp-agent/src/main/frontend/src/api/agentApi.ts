import type { DiscoveredTool } from '../types'

const BASE = '/api/agent'

export async function sendPrompt(prompt: string): Promise<string> {
  const res = await fetch(`${BASE}/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ prompt }),
  })
  if (!res.ok) {
    const text = await res.text().catch(() => 'Unknown error')
    throw new Error(`Request failed (${res.status}): ${text}`)
  }
  const data = (await res.json()) as { answer: string }
  return data.answer
}

export async function fetchTools(): Promise<DiscoveredTool[]> {
  const res = await fetch(`${BASE}/tools`)
  if (!res.ok) throw new Error(`Failed to fetch tools (${res.status})`)
  return res.json() as Promise<DiscoveredTool[]>
}
