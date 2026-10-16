# Architecture & Design — MCP Agent Demo

> A Spring Boot 4 + Spring AI 2.0 project demonstrating LLM-orchestrated tool calling
> over the Model Context Protocol (MCP), with a skill-based server architecture, a
> peer agent reached over a secured agent-to-agent link, per-server rate limiting, and
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
8. [Multi-Agent Design (employee-agent)](#8-multi-agent-design-employee-agent)
9. [Security](#9-security)
10. [Rate Limiting](#10-rate-limiting)
11. [Technology Stack](#11-technology-stack)
12. [Running the System](#12-running-the-system)
13. [Design Decisions](#13-design-decisions)
14. [Roadmap](#14-roadmap)

---

## 1. Terminology

The terms "agent", "orchestration", "skill", and "workflow" are used inconsistently
across the industry. This section defines how they are used in this project.

| Term | Definition in this project |
|---|---|
| **MCP (Model Context Protocol)** | An open standard for exposing callable functions (tools) to LLMs over HTTP. Each MCP server advertises a `tools/list` and handles `tools/call`. |
| **Tool** | A single callable function exposed by an MCP server — e.g. `calculate_simple_interest`. |
| **Skill** | A domain-scoped MCP server that groups related tools — e.g. `finance-mcp` groups all financial calculation tools. One server = one skill. |
| **Agent** | An LLM that has access to tools and a loop: it decides which tools to call, sees the results, and decides again until it can produce a final answer. This project has two: `mcp-agent` (primary) and `employee-agent` (peer, employee domain). |
| **Agent-to-agent (A2A) link** | A call from one agent to another agent over REST. Here: `mcp-agent` → `employee-agent`, exposed to the primary LLM as the local tool `ask_employee_agent` and secured with an `X-API-Key` header. This is a simple custom REST contract, **not** the Google A2A protocol. |
| **Tool-calling loop** | The back-and-forth between the LLM and the tool executor. Spring AI's `ChatClient` runs this loop internally — it is not visible as explicit code. |
| **Orchestration** | Coordination of multiple agents or tool calls. In this project, `OrchestrationService` is a single agent entry point — the LLM does the orchestration by chaining tool calls. |
| **Workflow** | A _fixed_ sequence of steps with no LLM deciding the path. Not implemented here — this project is fully LLM-driven. |
| **Sub-agent** | A second, specialised LLM instance that the primary orchestrator delegates to. `employee-agent` is the first one (employee domain). There is still no router — the primary LLM decides when to delegate. |
| **Rate limit** | A cap on tool calls. Each of `employee-mcp` and `finance-mcp` allows 100 calls/second shared across all of its tools. |
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

### Full topology (with employee-agent and employee-mcp)

```
                         User / Browser (React UI)
                                  │  POST /api/agent/chat · GET /api/agent/tools
                                  ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                       mcp-agent (:8080)  — primary LLM                   │
│  AgentController → OrchestrationService → ChatClient (Ollama)            │
│                                                │ tool-calling loop       │
│   tools = MCP tools (auto-discovered) + ask_employee_agent (local @Tool) │
└───┬───────────────┬──────────────┬──────────────┬───────────────┬────────┘
    │ MCP           │ MCP          │ MCP          │ MCP           │ REST + X-API-Key
    ▼               ▼              ▼              ▼               ▼
finance-mcp     research-mcp   comms-mcp    employee-mcp     employee-agent (:8086)
 (:8082)          (:8083)        (:8084)      (:8085)        own Ollama ChatClient
 100 RPS cap                                  100 RPS cap            │ MCP
 simple + compound                            get_employee_details   ▼
 interest (+batch)                            search_employees   employee-mcp (:8085)
```

`mcp-agent` has **two routes to employee data**: indirectly through `employee-agent`
(agent-to-agent), or directly through `employee-mcp`'s tools (agent-to-MCP). Both are
registered with the same LLM and it chooses; the system prompt allows either.

> **Animated version:** step through these call flows (direct MCP, agent-to-agent, wrong API key, rate limit) in the [interactive call-flow diagram](https://claude.ai/artifact/D8cDctBX5RcyD5BaDBNtyb). The page is private to its owner unless shared.

### What happens at startup

1. `mcp-agent` boots and its MCP client connects to each server listed in `application.yml`.
2. For each server, it calls `tools/list` and receives the tool schemas (name, description, JSON parameter schema).
3. Spring AI adapts each discovered tool into a `ToolCallback` and registers it with the `ChatClient` via `ToolCallbackProvider`.
4. The LLM now knows about all tools from every skill server (finance, research, comms, employee) — no code change needed to add a new skill.
5. `EmployeeAgentTools` (a regular Spring `@Component`, not discovered via MCP) is added to the same `ChatClient` as the local tool `ask_employee_agent`. `employee-agent` is **not** contacted at startup — it is called lazily on first use.

`employee-agent` runs the same discovery against `employee-mcp` at *its* startup, so `employee-mcp` must be up before `employee-agent`.

---

## 3. Module Reference

### 3.1 `mcp-agent` — The Agent (port 8080)

The primary agent. (`employee-agent`, §3.6, is the only other module with an LLM; all other modules are stateless tool servers.)

```
mcp-agent/
├── src/main/java/com/milind/mcpagent/
│   ├── Application.java
│   ├── api/
│   │   └── AgentController.java        ← REST: POST /chat, GET /tools
│   ├── config/
│   │   └── ChatClientConfig.java       ← Wires Ollama + system prompt + MCP tools + ask_employee_agent
│   ├── employee/
│   │   └── EmployeeAgentTools.java     ← @Tool ask_employee_agent: REST call to employee-agent with X-API-Key
│   └── domain/
│       └── OrchestrationService.java   ← Entry point for agent invocation
└── src/main/
    ├── frontend/                        ← React 19 app (built into target/classes/static)
    └── resources/application.yml       ← MCP client connections, Ollama model config
```

**Key classes:**

- `ChatClientConfig` — builds the `ChatClient` bean with the system prompt and all discovered MCP tools wired in. This is where the LLM's behaviour is defined.
- `OrchestrationService.orchestrate(prompt)` — single method that fires the tool-calling loop. Spring AI handles everything internally.
- `EmployeeAgentTools.askEmployeeAgent(question)` — local Spring AI `@Tool`. Builds a `RestClient` with `employee-agent.url` and a default `X-API-Key` header (`employee-agent.api-key`), then `POST /api/employee/ask`. It is a local tool, so it does **not** appear in `GET /api/agent/tools` (which lists only MCP-discovered tools).
- `AgentController` — thin REST wrapper. `POST /api/agent/chat` drives the agent; `GET /api/agent/tools` exposes discovered tools deterministically (no LLM, straight from `tools/list`).

---

### 3.2 `finance-mcp` — Finance Skill (port 8082)

**Domain:** financial calculations.

```
finance-mcp/
└── src/main/java/com/milind/financemcp/
    ├── api/FinanceMcpTools.java         ← @McpTool surface
    ├── domain/SimpleInterestCalculator.java
    ├── domain/CompoundInterestCalculator.java
    └── domain/BatchCompoundInterestService.java   (rate limiting: shared mcp-ratelimit)
```

**Tools exposed:**

| Tool | Description |
|---|---|
| `calculate_simple_interest` | Computes SI = (P × R × T) / 100. Returns interest and total amount as `BigDecimal` (no floating-point drift). |
| `calculate_compound_interest` | One principal. `A = P·(1 + r/(100·n))^(n·t)`. Inputs: principal, annual rate %, years, compounds per year (1 annual, 4 quarterly, 12 monthly). `years × n` must be a whole number of periods, otherwise the call is rejected rather than approximated. |
| `calculate_compound_interest_batch` | Many principals, same rate/term/compounding. Input: list of `{id, principal}` (max 500), e.g. `id` = employeeId, `principal` = salary. Returns per-item results plus `totalInterest` and `totalAmount`. |

All three tools call the shared rate limiter first (§10).

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

### 3.5 `employee-mcp` — Employee Skill (port 8085)

**Domain:** employee data. Stateless MCP server over in-memory sample data (E001–E008).

```
employee-mcp/
└── src/main/java/com/milind/employeemcp/
    ├── api/EmployeeMcpTools.java        ← @McpTool surface; parses/validates inputs
    ├── domain/Employee.java             ← record: id, name, dateOfBirth, region, country, state, salary
    ├── domain/EmployeeRepository.java   ← in-memory seed data (swap for a real store here)
    └── domain/EmployeeService.java      ← search logic   (rate limiting: shared mcp-ratelimit)
```

**Tools exposed:**

| Tool | Description |
|---|---|
| `get_employee_details` | One employee by `employeeId` (case-insensitive). Unknown id → tool error. |
| `search_employees` | Dynamic search. Optional filters: `employeeId`, `dateOfBirth` (exact), `dobFrom`/`dobTo` (inclusive range), `region`, `country`, `state`. Filters are **AND-combined**; text matches are case-insensitive; dates are `yyyy-MM-dd`. **At least one filter is required** so a call can never dump the whole table; `dobFrom > dobTo` is rejected. |

Salary is returned because it is the principal fed into the finance tools.

---

### 3.6 `employee-agent` — Employee peer agent (port 8086)

**Domain:** answering employee questions for `mcp-agent`. Has its own Ollama `ChatClient`
and its own MCP client to `employee-mcp`.

```
employee-agent/
└── src/main/java/com/milind/employeeagent/
    ├── api/EmployeeAgentController.java  ← POST /api/employee/ask {question} → {answer}
    ├── config/ChatClientConfig.java      ← system prompt + employee-mcp tools
    └── security/ApiKeyFilter.java        ← X-API-Key check on /api/**
```

Its system prompt tells it to always use the tools, never invent data, and return matching
employees' fields completely (id, name, dateOfBirth, region, country, state, salary) so the
calling agent can pass them on to finance tools. See §8 for the interaction and §9 for security.

---

### 3.7 `mcp-ratelimit` — Shared rate-limit library

A plain library jar (no `spring-boot-maven-plugin`) that finance-mcp and employee-mcp depend on.

```
mcp-ratelimit/
├── src/main/java/com/milind/mcpratelimit/
│   ├── ToolRateLimiter.java              ← Bucket4j token bucket; tryAcquire() / acquireOrThrow()
│   ├── RateLimitExceededException.java
│   ├── RateLimitProperties.java          ← mcp.rate-limit.requests-per-second (default 100, must be > 0)
│   └── RateLimitAutoConfiguration.java   ← registers ONE ToolRateLimiter bean (@ConditionalOnMissingBean)
└── src/main/resources/META-INF/spring/
    └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

A server just adds the dependency, sets the YAML property, and injects `ToolRateLimiter` into its
tool class. **Enforcement scope is unchanged by the extraction**: the bucket is per server (one bean
per application) and each tool method calls `acquireOrThrow()` first. Defining your own
`ToolRateLimiter` bean (e.g. Redis-backed) overrides the auto-configured one.

---

### 3.8 `general-purpose-mcp` — Legacy (port 8081)

The original single server that held all three tools before the skill split. It is
no longer connected to `mcp-agent` (the YAML connections now point to the three
skill servers). Kept for reference; safe to delete once the three skill servers are
confirmed stable.

### 3.9 Maven layout (multi-module)

```
mcp-agent-demo/                (root pom: com.milind:mcp-agent-demo, packaging=pom)
├── pom.xml                    ← parent: spring-boot-starter-parent 4.1.1; manages Spring AI BOM,
│                                 Bucket4j and mcp-ratelimit versions; lists <modules>
├── mcp-ratelimit/             ← library (auto-configuration)
├── finance-mcp/  employee-mcp/            ← depend on mcp-ratelimit
├── research-mcp/ comms-mcp/ general-purpose-mcp/
├── employee-agent/ mcp-agent/
```

Modules inherit `groupId` (`com.milind`) and `version` from the root. Only Maven coordinates were
unified; Java package names are unchanged by this restructuring. The `frontend-maven-plugin` stays
in `mcp-agent`. Build everything with `mvn test` from the root; see README → Build.

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
4. Employee data: call ask_employee_agent, or the employee-mcp tools directly. Never
   invent employee data.
5. Compound interest for employees: first get the employees and salaries, then use
   salary as the principal. ONE employee → calculate_compound_interest; MANY →
   calculate_compound_interest_batch once with every employee as an item
   (id = employeeId, principal = salary). If rate, years or compounds-per-year is
   missing, ask the user — do not assume.
6. If no tool applies, say so and list available tools.
7. Be concise and state which tool(s) you called.
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
  ├── calculate_simple_interest
  ├── calculate_compound_interest
  └── calculate_compound_interest_batch

research-mcp  (:8083)  ─── Research Skill
  └── search_learning_resources

comms-mcp     (:8084)  ─── Communication Skill
  └── send_greeting

employee-mcp  (:8085)  ─── Employee Skill
  ├── get_employee_details
  └── search_employees
```

### 5.3 Adding a new skill

Adding a new skill requires:

1. Create a new Spring Boot module (copy any existing skill module as template).
2. Annotate tool methods with `@McpTool` and `@McpToolParam`.
3. Add one entry to `mcp-agent/src/main/resources/application.yml`:

```yaml
spring.ai.mcp.client.streamable-http.connections:
  my-new-skill:
    url: http://localhost:8087      # pick a free port (8085 = employee-mcp, 8086 = employee-agent)
    endpoint: /mcp
```

No changes to `mcp-agent` Java code. The agent discovers the new tools automatically
at startup and the LLM can immediately invoke them.

### 5.4 Three-layer skill model

Skills can be thought of at three levels of sophistication:

```
Layer 3 — Sub-agents (first one implemented: employee-agent)
  A domain gets its own ChatClient with a tailored system prompt, reached over a
  secured REST link. There is no router yet: the primary LLM decides to delegate
  by calling ask_employee_agent. See §8.

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

### 7.3 Employee flow, one employee: "Compound interest on E001's salary at 7% for 3 years, annually"

```
Browser → POST /api/agent/chat → mcp-agent ChatClient → Ollama
  │  tool_calls = [ get_employee_details(E001) ]            (direct agent→MCP route)
  ▼
employee-mcp  tools/call get_employee_details
  │  ← { employeeId: E001, name: "Priya Sharma", ..., salary: 85000.00 }
  ▼
Ollama (pass 2) → tool_calls = [ calculate_compound_interest(85000, 7, 3, 1) ]
  ▼
finance-mcp   tools/call calculate_compound_interest
  │  ← { principal: 85000.00, interest: 19128.66, totalAmount: 104128.66 }
  ▼
Ollama (pass 3) → final text → { "answer": "..." }
```

The LLM may instead call `ask_employee_agent("details of E001")` for
the first hop — that variant is in §8.2.

### 7.4 Employee flow, many employees: "For all employees in India, compound interest at 7% for 3 years"

```
Ollama → tool_calls = [ search_employees(country="India") ]
  ▼
employee-mcp  ← [ {E001, salary 85000}, {E002, salary 92000} ]
  ▼
Ollama → tool_calls = [ calculate_compound_interest_batch(
             items=[{id:E001, principal:"85000"}, {id:E002, principal:"92000"}],
             annualRatePercent=7, years=3, compoundsPerYear=1) ]       ← ONE call, not N
  ▼
finance-mcp   ← { entries:[...per employee...], totalInterest, totalAmount }
  ▼
Ollama → final answer
```

The dynamic filters are resolved entirely in `employee-mcp`; the LLM only maps the user's
words onto filter arguments. The batch tool exists so a group costs one finance call and one
rate-limit token instead of N.

---

## 8. Multi-Agent Design (employee-agent)

### 8.1 Why a second agent

`employee-agent` demonstrates the *agent-calls-agent* pattern: a domain-specialised LLM with
its own system prompt and tool set that the primary agent can delegate to, instead of the
primary LLM holding every domain's rules. It also gives the A2A link something real to secure.

### 8.2 Interaction and flow (agent → agent)

```
User → mcp-agent (LLM #1)
          │ tool_call: ask_employee_agent("List all employees in India")
          ▼
   EmployeeAgentTools  (RestClient, header X-API-Key: <shared secret>)
          │ POST http://localhost:8086/api/employee/ask   {"question": "..."}
          ▼
   employee-agent  ── ApiKeyFilter ──✗──▶ 401 {"error":"missing or invalid X-API-Key"}
          │ ✓ key valid
          ▼
   EmployeeAgentController → ChatClient (LLM #2, employee system prompt)
          │ tool_call: search_employees(country="India")
          ▼
   employee-mcp (rate-limited, 100 RPS) ← employees incl. salaries
          │
          ▼  LLM #2 writes the answer text
   {"answer": "E001 Priya Sharma ... salary 85000.00; E002 ..."}
          │
          ▼
   back in mcp-agent: the string is the tool result → LLM #1 continues
          → e.g. calculate_compound_interest_batch on finance-mcp → final answer
```

Contract (the only coupling between the two agents):

| | |
|---|---|
| Request | `POST /api/employee/ask`, header `X-API-Key`, body `{"question": "<natural language>"}` |
| 200 | `{"answer": "<text>"}` |
| 400 | blank question |
| 401 | missing/incorrect key |

### 8.3 Trade-offs of this design

- **The hand-off is free text.** `employee-agent` returns prose, and `mcp-agent`'s LLM must
  re-parse salaries out of it before calling finance. With a small local model this is the
  weakest link (two LLM hops can drop or garble a value). The direct route
  (`search_employees` → structured JSON → finance) avoids this, which is why both routes exist.
- **Two LLM passes = more latency** than the direct MCP route.
- **No router.** Which route is used depends on the primary LLM's judgement of the tool descriptions.
- **Statelessness.** Each delegated question is independent; no shared conversation memory.

---

## 9. Security

### 9.1 Threat model and scope

This is a local demo with a **deliberately simple** control: one hop is authenticated; the rest
is trusted-network. Do not treat it as production-grade.

| Link | Protection | Notes |
|---|---|---|
| Browser → mcp-agent | none | no user authentication |
| **mcp-agent → employee-agent** | **Shared secret, `X-API-Key` header** | enforced by `ApiKeyFilter` |
| mcp-agent / employee-agent → MCP servers | none (rate limit only, §10) | any caller that can reach the port can call tools |
| Transport | plain HTTP | no TLS |

### 9.2 How the agent-to-agent control works

- `ApiKeyFilter` is a servlet `OncePerRequestFilter` that applies only to paths starting with
  `/api/`. A missing or incorrect header returns **401** before the request reaches the
  controller — so no LLM call or MCP call is made for an unauthenticated request.
- The comparison uses `MessageDigest.isEqual` (constant time) to avoid a timing side channel.
- The service refuses to start if `agent.security.api-key` is blank.
- Rejections are logged (`Rejected request to … missing or invalid X-API-Key`); the key value is never logged.
- Plain servlet filter by design — no Spring Security dependency — to keep the control easy to read.

### 9.3 Key configuration

| Side | Property | Default |
|---|---|---|
| mcp-agent (sender) | `employee-agent.api-key` | `${AGENT_SHARED_API_KEY:dev-only-shared-key}` |
| employee-agent (verifier) | `agent.security.api-key` | `${AGENT_SHARED_API_KEY:dev-only-shared-key}` |

Both read the same environment variable, so exporting `AGENT_SHARED_API_KEY` before starting
both processes keeps them in sync. **The default is for local use only.**

### 9.4 Known limitations / hardening path

- Static shared secret: rotation requires restarting both services; compromise of one side exposes the other.
- No TLS — the key travels in clear text on the wire. Use HTTPS (or a service mesh) outside localhost.
- No per-caller identity, scopes, or audit trail. Next step: short-lived signed JWTs (issuer, audience, expiry) or mTLS.
- MCP servers are unauthenticated; Streamable HTTP supports bearer headers if needed.
- Prompt injection: tool outputs (employee data, `employee-agent` prose) are fed back to an LLM; there is no output sanitisation.
- Salary and DOB are sensitive data flowing through LLM prompts; fine for sample data, not for real HR data.

---

## 10. Rate Limiting

### 10.1 Requirement and scope

Max **100 requests per second across all tools** — implemented **per MCP server**: `employee-mcp`
and `finance-mcp` each hold one shared budget of 100 calls/sec across *all of their own tools*.
(There is no single global counter across servers, and `research-mcp`/`comms-mcp` are not limited.)

### 10.2 Design

```
tool method (e.g. search_employees)
   │  1. rateLimiter.acquireOrThrow()      ← first statement, before any work
   │         ├─ window has < 100 calls in the last 1s → record timestamp, continue
   │         └─ else → throw RateLimitExceededException("Rate limit exceeded: max 100 ...")
   │  2. do the work
   ▼
MCP response: result.isError = true with the message  (client/LLM sees the text)
```

- `ToolRateLimiter` wraps a **Bucket4j token bucket** (`bucket4j_jdk17-core`): capacity = refill =
  100, greedy refill over 1s. A burst of up to 100 calls is admitted instantly; after that, tokens
  return continuously, so the long-run average is capped at 100/s. This is the accepted
  semantic ("burstable is acceptable"): in a worst-case window slightly more than 100 can pass
  (the burst plus tokens refilled during that second). A strict never-above-100-in-any-second cap
  would need a sliding-window counter or an extra smaller limit (e.g. 10 per 100 ms).
- One `@Bean` per server (`RateLimitConfig`), injected into the tool class, so every tool
  draws from the same budget. Limit is configurable: `mcp.rate-limit.requests-per-second` (default 100).
- Lock-free and constant memory (one bucket state).
- Rejected calls are **not** queued or retried by the server; the caller must back off.
- The limiter lives once, in the shared `mcp-ratelimit` module (§3.7), and is auto-configured into
  each server; there is no per-server copy.
- Going multi-instance means supplying a `ToolRateLimiter` backed by a Bucket4j `ProxyManager`
  (Redis/JCache/Hazelcast) — as an override bean or a new factory in `mcp-ratelimit`. Tool code does not change.

### 10.3 Verified behaviour

A parallel burst of 300 `get_employee_details` calls against `employee-mcp` produced 122
successes and 178 `isError: true` rejections: the 100-call burst plus tokens refilled while the
burst ran (see README for the command). Unit tests use a fake clock to assert burst, full refill
and proportional partial refill.

### 10.4 Limitations

- In-process: with more than one replica of a server, the effective limit is 100 × replicas.
  Use a Bucket4j distributed backend (Redis) or a gateway for a true cluster-wide cap — not yet implemented.
- Global per server, not per client: one noisy caller can exhaust the budget for everyone.
- The rejection surfaces as a tool error the LLM must interpret; `mcp-agent` does not auto-retry.

### 10.5 Server-level vs tool-level limiting

The limit is enforced **at the tool level** (the requirement): it counts only `tools/call`
executions, returns an MCP tool error the model can read, and can later be weighted or split per tool.
It does not protect the HTTP layer (`initialize`, `tools/list`, malformed floods, SSE keep-alives).
Recommended layering for deployment: a coarse per-client limit at the gateway/ingress (HTTP 429)
**plus** this tool-level limit (and input bounds/timeouts) behind it.

---

## 11. Technology Stack

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

## 12. Running the System

### Port assignments

| Process | Port | Purpose |
|---|---|---|
| Ollama | 11434 | Local LLM inference |
| `mcp-agent` | 8080 | Agent REST API + serves React UI |
| `finance-mcp` | 8082 | Finance Skill MCP server |
| `research-mcp` | 8083 | Research Skill MCP server |
| `comms-mcp` | 8084 | Communication Skill MCP server |
| `employee-mcp` | 8085 | Employee Skill MCP server (100 RPS cap) |
| `employee-agent` | 8086 | Peer agent, REST `/api/employee/ask` (X-API-Key) |
| React dev server | 5173 | Development only (proxies /api → :8080) |

### Startup order

The skill servers must be up _before_ their MCP client starts, because the client
connects and calls `tools/list` at startup. If one is unavailable, that client's app
will fail to start. Dependency order: `employee-mcp` → `employee-agent`;
all skill servers → `mcp-agent`. (`mcp-agent` calls `employee-agent` lazily, so it can
start before it, but delegation fails until `employee-agent` is up.)

```bash
# 1. Ensure Ollama is running
ollama serve                    # or start via the Ollama desktop app

# 2. Start skill servers (order between them doesn't matter)
cd finance-mcp  && mvn spring-boot:run   # terminal 1
cd research-mcp && mvn spring-boot:run   # terminal 2
cd comms-mcp    && mvn spring-boot:run   # terminal 3
cd employee-mcp && mvn spring-boot:run   # terminal 3b

# 2b. Start the peer agent (needs employee-mcp). Same key as mcp-agent!
export AGENT_SHARED_API_KEY='a-long-random-value'   # export in the mcp-agent terminal too
cd employee-agent && mvn spring-boot:run # terminal 3c

# 3. Start the agent (after the skill servers are ready)
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

## 13. Design Decisions

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

### Why add a second LLM (employee-agent) after all?

Originally a single LLM sufficed. `employee-agent` was added as a deliberate step to
demonstrate agent-to-agent delegation and to give the A2A link a security boundary.
The cost is real (two LLM hops, free-text hand-off, extra Ollama load — see §8.3), so the
direct `employee-mcp` route is kept as the structured, lower-latency path. A second LLM is
justified when a domain needs its own prompt/model/memory or an independent trust boundary.

### Why a plain REST link instead of the A2A protocol or MCP for agent-to-agent?

A single `POST` with a shared key is the smallest thing that demonstrates delegation plus
authentication. Exposing `employee-agent` as an MCP server would let `mcp-agent` discover it
automatically but would hide the security boundary; the A2A protocol adds agent cards and
JSON-RPC machinery beyond this demo's scope.

### Why an API key and a servlet filter rather than Spring Security / JWT?

The requirement was "simple security". A constant-time key check in one small filter is easy
to audit and has no extra dependencies. JWT/mTLS is the documented hardening path (§9.4).

### Why Bucket4j instead of the hand-written limiter?

The first version was a ~30-line in-house sliding window (strict, but per-JVM only). Since higher
environments will run several instances, we moved to Bucket4j: a maintained, lock-free token
bucket whose Redis/JCache/Hazelcast backends give a cluster-wide limit without changing the tool
code. The trade-off, accepted explicitly, is burst semantics instead of a strict per-second ceiling.

### Why a batch compound-interest tool?

"A bunch of employees" would otherwise be N finance calls, N LLM round-trips and N rate-limit
tokens. One batch call keeps the LLM's job to a single structured tool call and the arithmetic
in `BigDecimal` on the server.

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

## 14. Roadmap

The following are natural next steps, in rough priority order.

### Layer 1 — Prompt personas (low effort, high value)

Add domain-specific behaviour rules to the system prompt in `ChatClientConfig`:
- When the topic is financial, respond as a brief financial advisor with a risk note.
- When the topic is research/learning, always present resources with category headings.

### Layer 2 — Richer skill servers (medium effort)

Extend existing skill servers with more tools:
- `finance-mcp`: loan amortization, currency conversion (compound interest is done).
- `research-mcp`: swap hardcoded resources for live Tavily/Brave Search API call.
- `comms-mcp`: email/Slack notification tool.

### Security & rate-limit hardening (see §9.4, §10.4)

Signed JWT or mTLS between agents, TLS everywhere, authenticated MCP servers, per-client
quotas, and a shared (cluster-wide) limiter.

### Layer 3 — Sub-agents (first one done; router still open)

`employee-agent` is the first sub-agent, reached by explicit tool call. Remaining work: a
router and more domain agents. Split `OrchestrationService` into a router + domain agents:

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
