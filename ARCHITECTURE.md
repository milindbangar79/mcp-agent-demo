# Architecture & Design — MCP Agent Demo

> A Spring Boot 4 + Spring AI 2.0 project demonstrating LLM-orchestrated tool calling
> over the Model Context Protocol (MCP), with a skill-based server architecture and
> a React 19 frontend.

---

## Table of Contents

1. [Terminology](#1-terminology)
2. [System Overview](#2-system-overview)
3. [Module Reference](#3-module-reference)
4. [Agent Design](#4-agent-design)
5. [Skill Architecture](#5-skill-architecture)
6. [Frontend Architecture](#6-frontend-architecture)
7. [Data Flow](#7-data-flow)
8. [Technology Stack](#8-technology-stack)
9. [Running the System](#9-running-the-system)
10. [Design Decisions](#10-design-decisions)
11. [Roadmap](#11-roadmap)

---

## 1. Terminology

The terms "agent", "orchestration", "skill", and "workflow" are used inconsistently
across the industry. This section defines how they are used in this project.

| Term | Definition in this project |
|---|---|
| **MCP (Model Context Protocol)** | An open standard for exposing callable functions (tools) to LLMs over HTTP. Each MCP server advertises a `tools/list` and handles `tools/call`. |
| **Tool** | A single callable function exposed by an MCP server — e.g. `calculate_simple_interest`. |
| **Skill** | A domain-scoped MCP server that groups related tools — e.g. `finance-mcp` groups all financial calculation tools. One server = one skill. |
| **Agent** | An LLM that has access to tools and a loop: it decides which tools to call, sees the results, and decides again until it can produce a final answer. `mcp-agent` is the agent in this project. |
| **Tool-calling loop** | The back-and-forth between the LLM and the tool executor. Spring AI's `ChatClient` runs this loop internally — it is not visible as explicit code. |
| **Orchestration** | Coordination of multiple agents or tool calls. In this project, `OrchestrationService` is a single agent entry point — the LLM does the orchestration by chaining tool calls. |
| **Workflow** | A _fixed_ sequence of steps with no LLM deciding the path. Not implemented here — this project is fully LLM-driven. |
| **Sub-agent** | A second, specialised LLM instance that the primary orchestrator delegates to. Not implemented yet — see Roadmap. |
| **System prompt** | The standing instruction to the LLM that defines its persona, rules, and tool-use behaviour. Lives in `ChatClientConfig`. |

---

## 2. System Overview

```
┌────────────────────────────────────────────────────────────────────────┐
│                          User / Browser                                │
│                    http://localhost:8080 (React UI)                    │
└────────────────────────────┬───────────────────────────────────────────┘
                             │  POST /api/agent/chat
                             │  GET  /api/agent/tools
                             ▼
┌────────────────────────────────────────────────────────────────────────┐
│                          mcp-agent  (:8080)                            │
│                                                                        │
│   AgentController  ──▶  OrchestrationService  ──▶  ChatClient         │
│                                                         │              │
│                                                   Ollama LLM           │
│                                                  (llama3.1:8b)         │
│                                                         │              │
│                              ┌──────────────────────────┘              │
│                              │  tool-calling loop (Spring AI)          │
│                              │                                         │
│              ┌───────────────┼────────────────┐                        │
│              ▼               ▼                ▼                        │
│        finance-mcp     research-mcp      comms-mcp                     │
│          (:8082)         (:8083)           (:8084)                     │
└────────────────────────────────────────────────────────────────────────┘
       │                   │                    │
       ▼                   ▼                    ▼
  Streamable HTTP    Streamable HTTP      Streamable HTTP
  /mcp endpoint      /mcp endpoint        /mcp endpoint
```

### What happens at startup

1. `mcp-agent` boots and its MCP client connects to each server listed in `application.yml`.
2. For each server, it calls `tools/list` and receives the tool schemas (name, description, JSON parameter schema).
3. Spring AI adapts each discovered tool into a `ToolCallback` and registers it with the `ChatClient` via `ToolCallbackProvider`.
4. The LLM now knows about all tools from all three skill servers — no code change needed to add a new skill.

---

## 3. Module Reference

### 3.1 `mcp-agent` — The Agent (port 8080)

The only module with an LLM. All other modules are stateless tool servers.

```
mcp-agent/
├── src/main/java/com/yourorg/mcpagent/
│   ├── Application.java
│   ├── api/
│   │   └── AgentController.java        ← REST: POST /chat, GET /tools
│   ├── config/
│   │   └── ChatClientConfig.java       ← Wires Ollama + system prompt + tools
│   └── domain/
│       └── OrchestrationService.java   ← Entry point for agent invocation
└── src/main/
    ├── frontend/                        ← React 19 app (built into target/classes/static)
    └── resources/application.yml       ← MCP client connections, Ollama model config
```

**Key classes:**

- `ChatClientConfig` — builds the `ChatClient` bean with the system prompt and all discovered MCP tools wired in. This is where the LLM's behaviour is defined.
- `OrchestrationService.orchestrate(prompt)` — single method that fires the tool-calling loop. Spring AI handles everything internally.
- `AgentController` — thin REST wrapper. `POST /api/agent/chat` drives the agent; `GET /api/agent/tools` exposes discovered tools deterministically (no LLM, straight from `tools/list`).

---

### 3.2 `finance-mcp` — Finance Skill (port 8082)

**Domain:** financial calculations.

```
finance-mcp/
└── src/main/java/com/milind/financemcp/
    ├── api/FinanceMcpTools.java         ← @McpTool surface
    └── domain/SimpleInterestCalculator.java
```

**Tools exposed:**

| Tool | Description |
|---|---|
| `calculate_simple_interest` | Computes SI = (P × R × T) / 100. Returns interest and total amount as `BigDecimal` (no floating-point drift). |

---

### 3.3 `research-mcp` — Research Skill (port 8083)

**Domain:** learning resources and curated content.

```
research-mcp/
└── src/main/java/com/milind/researchmcp/
    ├── api/ResearchMcpTools.java
    └── domain/LearningResourcesService.java
```

**Tools exposed:**

| Tool | Description |
|---|---|
| `search_learning_resources` | Returns curated calculators, articles, and training content for a given financial topic. Currently hardcoded; designed to swap in a live search API (e.g. Tavily) without changing the tool signature. |

**Returned structure:**
```json
{
  "topic": "simple interest",
  "calculators": [ { "name": "...", "url": "...", "description": "..." } ],
  "articles":    [ { "name": "...", "url": "...", "description": "..." } ],
  "trainingContent": [ { "name": "...", "url": "...", "description": "..." } ]
}
```

---

### 3.4 `comms-mcp` — Communication Skill (port 8084)

**Domain:** messaging and greetings.

```
comms-mcp/
└── src/main/java/com/milind/commsmcp/
    ├── api/CommsMcpTools.java
    └── domain/GreetingService.java
```

**Tools exposed:**

| Tool | Description |
|---|---|
| `send_greeting` | Returns a personalised or generic greeting. Minimal today; the entry point for email/notification tools in future. |

---

### 3.5 `general-purpose-mcp` — Legacy (port 8081)

The original single server that held all three tools before the skill split. It is
no longer connected to `mcp-agent` (the YAML connections now point to the three
skill servers). Kept for reference; safe to delete once the three skill servers are
confirmed stable.

---

## 4. Agent Design

### 4.1 How the tool-calling loop works

Spring AI's `ChatClient` implements the loop transparently. The sequence inside a
single `orchestrate(prompt)` call is:

```
1. Build messages: [ system-prompt, user-prompt ]
2. Send to Ollama with full tool schemas attached
3. LLM replies with: tool_calls = [ { name, arguments } ]
4. Spring AI executes each tool call against the MCP server
5. Tool results appended to message history
6. LLM sees results → may call more tools or produce final text
7. Loop exits when LLM produces a text reply with no tool calls
8. Return final text to caller
```

From `OrchestrationService.java`, this entire loop is:

```java
return chatClient.prompt()
        .user(prompt)
        .call()
        .content();
```

The complexity is inside Spring AI. `OrchestrationService` is the _entry point_, not
the _orchestration engine_.

### 4.2 System prompt design

Defined in `ChatClientConfig.SYSTEM_PROMPT`. The rules are:

```
1. Always call a tool rather than answering from memory.
2. For calculations, always use the tool — never compute yourself.
3. When a calculation tool is called, ALWAYS also call search_learning_resources
   with the relevant topic. Include ALL returned items verbatim — do not filter,
   summarize, or omit any. Organize under: Online Calculators, Articles,
   Training Content.
4. If no tool applies, say so and list available tools.
5. Be concise and state which tool(s) you called.
```

**Why rule 3 is explicit about "do not filter":** LLMs trained with RLHF learn to
produce concise responses. Without the explicit instruction, the model will pick
1–2 resources from each category and omit the rest. Negative instructions
(`do not omit`) are necessary to override this default behaviour.

### 4.3 Temperature

Set to `0.2` in `application.yml`. Low temperature makes tool selection more
deterministic. Higher values make the model more creative but less reliable about
_when_ to call a tool versus answering from training data.

---

## 5. Skill Architecture

### 5.1 What a skill is

In this project, a **skill** is an MCP server scoped to a single domain. This maps
directly to equivalent concepts in other frameworks:

| This project | Semantic Kernel | LangChain | Description |
|---|---|---|---|
| MCP server (skill) | Plugin / Skill | Tool set / Chain | A named group of related capabilities |
| `@McpTool` method | `@KernelFunction` | `@tool` decorator | A single callable function |
| `ToolCallbackProvider` | Planner | Agent executor | What selects and runs tools |
| `ChatClient` loop | Planner loop | Agent loop | LLM decides → tool runs → LLM decides |

### 5.2 Current skill map

```
finance-mcp   (:8082)  ─── Finance Skill
  └── calculate_simple_interest

research-mcp  (:8083)  ─── Research Skill
  └── search_learning_resources

comms-mcp     (:8084)  ─── Communication Skill
  └── send_greeting
```

### 5.3 Adding a new skill

Adding a new skill requires:

1. Create a new Spring Boot module (copy any existing skill module as template).
2. Annotate tool methods with `@McpTool` and `@McpToolParam`.
3. Add one entry to `mcp-agent/src/main/resources/application.yml`:

```yaml
spring.ai.mcp.client.streamable-http.connections:
  my-new-skill:
    url: http://localhost:8085
    endpoint: /mcp
```

No changes to `mcp-agent` Java code. The agent discovers the new tools automatically
at startup and the LLM can immediately invoke them.

### 5.4 Three-layer skill model

Skills can be thought of at three levels of sophistication:

```
Layer 3 — Sub-agents (separate LLMs, not yet implemented)
  Each domain gets its own ChatClient with a tailored system prompt.
  The orchestrator routes requests to the correct sub-agent.

Layer 2 — Skill Packs (current implementation)
  Each domain is an MCP server. One LLM discovers and chains all tools.
  The agent handles cross-skill requests (e.g. calculate + look up resources)
  in a single tool-calling loop.

Layer 1 — Prompt personas (extends Layer 2)
  The system prompt adds domain-specific behaviour instructions:
  "When the topic is financial, take the role of a concise financial advisor."
  Activates automatically based on topic, not explicit routing.
```

---

## 6. Frontend Architecture

A React 19 single-page application served by Spring Boot from `target/classes/static`.

### 6.1 Structure

```
mcp-agent/src/main/frontend/
├── package.json              ← Salt DS 1.67, React 19.0.2, Vite 8, TypeScript 5.9
├── vite.config.ts            ← builds to target/classes/static; proxies /api → :8080
├── index.html                ← Inter font (Google Fonts)
└── src/
    ├── main.tsx              ← entry; imports @salt-ds/theme/index.css
    ├── App.tsx               ← SaltProvider (mode), layout shell, mode persistence
    ├── index.css             ← CSS custom properties for light/dark; all layout
    ├── types.ts              ← Message, DiscoveredTool interfaces
    ├── api/agentApi.ts       ← fetch wrappers: sendPrompt(), fetchTools()
    └── components/
        ├── Header.tsx        ← brand + Light/Dark toggle (persisted in localStorage)
        ├── ToolsPanel.tsx    ← sidebar: calls GET /api/agent/tools on mount
        ├── ChatPanel.tsx     ← message history + textarea input + send button
        └── ChatMessage.tsx   ← individual message bubble (user right, agent left)
```

### 6.2 Layout

```
┌─────────────────────────────────────────────────────────┐
│  ● MCP Agent                              [☀️ Light]    │  ← Header.tsx
├──────────────────┬──────────────────────────────────────┤
│ Available Tools  │                                      │
│ ─────────────── │  [User bubble]                  You  │
│ finance-mcp      │                                      │
│  ▸ calculate_si  │  Agent                               │
│                  │  [Agent bubble — full answer with    │
│ research-mcp     │   tool results and resources]        │
│  ▸ search_learn  │                                      │
│                  │  ···  (thinking animation)           │
│ comms-mcp        │                                      │
│  ▸ send_greeting ├──────────────────────────────────────┤
│                  │ [prompt textarea]         [Send →]   │
└──────────────────┴──────────────────────────────────────┘
  ToolsPanel.tsx              ChatPanel.tsx
```

### 6.3 Theming

- **Salt Design System** (`@salt-ds/core`, `@salt-ds/theme`) provides component-level
  light/dark via `<SaltProvider mode="light|dark">`.
- Layout-level theming uses CSS custom properties (`--bg`, `--surface`, `--border`,
  `--accent`, etc.) toggled by `body[data-mode="dark"]` set via `useEffect`.
- User's preference is persisted in `localStorage` under `mcp-agent-mode`.

### 6.4 Build integration

`frontend-maven-plugin` (v1.15.0) wires the React build into `mvn package`:

```
mvn package
  → installs Node 20
  → npm install
  → tsc -b && vite build
  → output lands in target/classes/static/
  → Spring Boot serves at http://localhost:8080/
```

For development, `npm run dev` (port 5173) proxies `/api` to `:8080`, so the
Spring Boot agent must be running separately.

---

## 7. Data Flow

### 7.1 Typical request: "Calculate SI on 10000 at 7.5% for 3 years"

```
Browser
  │
  │  POST /api/agent/chat
  │  { "prompt": "Calculate SI on 10000 at 7.5% for 3 years" }
  ▼
AgentController.chat()
  │
  ▼
OrchestrationService.orchestrate(prompt)
  │
  ▼
ChatClient.prompt().user(prompt).call()          ← Spring AI takes over
  │
  │  [message: system-prompt + user-prompt + all tool schemas]
  ▼
Ollama (llama3.1:8b, temp=0.2)
  │
  │  [LLM reply: tool_calls = [calculate_simple_interest, search_learning_resources]]
  ▼
Spring AI tool executor
  ├── POST http://localhost:8082/mcp  (finance-mcp)
  │     method: tools/call
  │     tool:   calculate_simple_interest
  │     args:   { principal: "10000", annualRatePercent: "7.5", years: "3" }
  │     ← { interest: 2250.00, totalAmount: 12250.00, ... }
  │
  └── POST http://localhost:8083/mcp  (research-mcp)
        method: tools/call
        tool:   search_learning_resources
        args:   { topic: "simple interest" }
        ← { calculators: [...], articles: [...], trainingContent: [...] }
  │
  │  [message appended: both tool results]
  ▼
Ollama (second pass — synthesizes results)
  │
  │  [final text reply with calculation + all resources, no more tool calls]
  ▼
ChatClient returns content string
  │
  ▼
AgentController  →  { "answer": "..." }
  │
  ▼
Browser renders in ChatPanel as agent message bubble
```

### 7.2 Tool discovery (startup + GET /tools)

```
mcp-agent starts
  │
  ├── MCP client connects to finance-mcp (:8082)
  │     GET tools/list → [{ name: "calculate_simple_interest", ... }]
  │
  ├── MCP client connects to research-mcp (:8083)
  │     GET tools/list → [{ name: "search_learning_resources", ... }]
  │
  └── MCP client connects to comms-mcp (:8084)
        GET tools/list → [{ name: "send_greeting", ... }]
  │
  ▼
ToolCallbackProvider (Spring AI) — all 3 tools registered
  │
  ▼
ChatClient built with ToolCallbackProvider + system prompt
  │
  ▼  (later, browser loads)
GET /api/agent/tools
  │
  ▼
OrchestrationService.discoverTools()
  │   reads from McpSyncClient.listTools() — no LLM involved
  ▼
[{ server: "finance-mcp", name: "calculate_simple_interest", ... }, ...]
  │
  ▼
ToolsPanel renders each tool as a card grouped by server
```

---

## 8. Technology Stack

| Layer | Technology | Version | Why |
|---|---|---|---|
| Language | Java | 21 | Virtual threads, records, pattern matching |
| Framework | Spring Boot | 4.1.0 | Production-grade DI, web server, autoconfiguration |
| AI integration | Spring AI | 2.0.0 | MCP client/server, tool callbacks, ChatClient abstraction |
| LLM | Ollama (llama3.1:8b) | local | No API key, no cost, runs offline |
| MCP transport | Streamable HTTP | — | SSE is deprecated in Spring AI 2.0; Streamable HTTP is the successor |
| Frontend framework | React | 19.0.2 | Concurrent rendering, stable hooks API |
| Build tool (frontend) | Vite | 8.2.0 | Fast HMR, native ESM |
| Type checking | TypeScript | 5.9.3 | Strict mode, full type safety |
| UI design system | Salt DS (JP Morgan) | 1.67.0 | Production-grade components, built-in light/dark mode |
| Frontend–Maven bridge | frontend-maven-plugin | 1.15.0 | Single `mvn package` builds both Java and React |
| Arithmetic | `java.math.BigDecimal` | — | Exact decimal arithmetic for financial values |

---

## 9. Running the System

### Port assignments

| Process | Port | Purpose |
|---|---|---|
| Ollama | 11434 | Local LLM inference |
| `mcp-agent` | 8080 | Agent REST API + serves React UI |
| `finance-mcp` | 8082 | Finance Skill MCP server |
| `research-mcp` | 8083 | Research Skill MCP server |
| `comms-mcp` | 8084 | Communication Skill MCP server |
| React dev server | 5173 | Development only (proxies /api → :8080) |

### Startup order

The skill servers must be up _before_ `mcp-agent` starts, because the MCP client
connects and calls `tools/list` at startup. If a skill server is unavailable,
`mcp-agent` will fail to start.

```bash
# 1. Ensure Ollama is running
ollama serve                    # or start via the Ollama desktop app

# 2. Start skill servers (order between them doesn't matter)
cd finance-mcp  && mvn spring-boot:run   # terminal 1
cd research-mcp && mvn spring-boot:run   # terminal 2
cd comms-mcp    && mvn spring-boot:run   # terminal 3

# 3. Start the agent (after all three skill servers are ready)
cd mcp-agent    && mvn spring-boot:run   # terminal 4

# 4. Open the UI
open http://localhost:8080
```

### Development (React hot-reload)

```bash
# Spring Boot must be running on :8080
cd mcp-agent/src/main/frontend
npm run dev               # → http://localhost:5173 (hot reload, proxies /api)
```

---

## 10. Design Decisions

### Why MCP instead of direct REST calls between services?

MCP provides a standard, model-agnostic interface. The agent doesn't know or care
that `calculate_simple_interest` is implemented in Java — it just sees a JSON schema.
Swapping the implementation (e.g. rewriting in Python) requires zero changes to the
agent. A direct REST call would couple the agent to the tool's implementation detail.

### Why Ollama (local LLM) instead of a cloud API?

No API key, no cost, no data leaving the machine. Any model on Ollama that supports
tool calling works (`llama3.1`, `qwen2.5`, `mistral`). For production, the only
change needed is replacing the Ollama Spring AI starter with the OpenAI or Anthropic
starter — the `ChatClient` API is the same.

### Why split `general-purpose-mcp` into three skill servers?

A single MCP server with all tools works technically, but has two problems at scale:
1. **Operational coupling** — deploying a new financial tool requires restarting a
   server that also handles communication tools.
2. **LLM context pollution** — giving the LLM 30 unrelated tools in one flat list
   increases the chance of wrong tool selection. Domain-scoped servers make the
   tool set per server smaller and more semantically coherent.

### Why not two LLMs / sub-agents yet?

Two LLMs are needed only when the domains require _different LLM configurations_
(e.g. different temperatures, different base models, or isolated conversation memory).
With one LLM and three tool domains, the tool-calling loop handles cross-domain
requests naturally. Adding a second LLM adds operational complexity (two Ollama
model loads, manual routing logic) without a clear benefit at this scale.

### Why `BigDecimal` for financial arithmetic?

`double` and `float` use binary floating point — `0.1 + 0.2 != 0.3`. For any
financial calculation displayed to a user, this is unacceptable. `BigDecimal` gives
exact decimal arithmetic with configurable rounding. All financial tools in this
project use `BigDecimal` throughout the domain layer.

### Why hardcode the learning resources in `research-mcp`?

To demonstrate the multi-tool orchestration pattern without an API key dependency.
The tool signature (`search_learning_resources(topic)`) is designed for live search:
swapping the implementation to call Tavily or Brave Search requires changing only
`LearningResourcesService.findResources()` — no changes to the tool annotation,
the agent, or the frontend.

### Why "do not filter, summarize, or omit any" in the system prompt?

LLMs trained with RLHF default to concise responses. When given a list of 9
resources, the model will pick the 2–3 it judges most relevant and omit the rest.
Negative instructions in the system prompt override this default. Without them,
the tool result is correct but the LLM's response silently drops data.

---

## 11. Roadmap

The following are natural next steps, in rough priority order.

### Layer 1 — Prompt personas (low effort, high value)

Add domain-specific behaviour rules to the system prompt in `ChatClientConfig`:
- When the topic is financial, respond as a brief financial advisor with a risk note.
- When the topic is research/learning, always present resources with category headings.

### Layer 2 — Richer skill servers (medium effort)

Extend existing skill servers with more tools:
- `finance-mcp`: compound interest, loan amortization, currency conversion.
- `research-mcp`: swap hardcoded resources for live Tavily/Brave Search API call.
- `comms-mcp`: email/Slack notification tool.

### Layer 3 — Sub-agents (higher effort, architecturally significant)

Split `OrchestrationService` into a router + domain agents:

```java
// Router LLM — lightweight, just classifies the request
String domain = routerClient.prompt()
    .user("Classify as 'finance', 'research', or 'comms': " + prompt)
    .call().content();

// Domain agent — specialised ChatClient with domain-specific system prompt
return agents.get(domain).prompt().user(prompt).call().content();
```

Each domain agent gets its own `ChatClient` bean wired to only its skill server's
tools and a tailored system prompt. This enables different temperatures, models, or
even different LLM providers per domain.

### Layer 4 — Agent memory (stateful conversations)

Currently each `/api/agent/chat` call is stateless — the LLM has no memory of prior
turns. Adding conversation history requires:
- Storing `Message` history server-side (keyed by session ID).
- Passing prior messages in each `ChatClient` call.
- Spring AI `ChatMemory` API supports this directly.

### Layer 5 — Real web search

Replace `LearningResourcesService` hardcoded data with a live Tavily API call.
Requires only a method-level change in `research-mcp` — no agent or frontend changes.

```java
// future implementation
public LearningResources findResources(String topic) {
    TavilyResponse response = tavilyClient.search(topic);
    return mapToLearningResources(topic, response);
}
```
