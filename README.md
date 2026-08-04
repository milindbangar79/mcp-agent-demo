# MCP Agent Demo — Spring Boot 4.1 + Spring AI 2.0

**GitHub:** https://github.com/milindbangar79/mcp-agent-demo

An LLM-driven agent that discovers and orchestrates tools from **three domain-scoped
MCP skill servers**, with a React 19 frontend. The agent uses a local Ollama model —
no API key or cloud dependency.

> For full design rationale, data flows, and roadmap see [ARCHITECTURE.md](./ARCHITECTURE.md).

---

## Architecture

```
Browser (React 19 / Salt DS)
         │  http://localhost:8080
         ▼
    mcp-agent (:8080)
    Spring AI ChatClient + Ollama (llama3.1:8b)
    Discovers tools at startup → tool-calling loop at runtime
         │
         ├──▶ finance-mcp  (:8082)   Finance Skill
         │      └── calculate_simple_interest
         │
         ├──▶ research-mcp (:8083)   Research Skill
         │      └── search_learning_resources
         │
         └──▶ comms-mcp   (:8084)   Communication Skill
                └── send_greeting
```

Each MCP server is a standalone Spring Boot app. Adding a new skill = new module +
one YAML entry in `mcp-agent`. No agent code changes.

---

## Modules

| Module | Port | Role |
|---|---|---|
| `mcp-agent` | 8080 | Agent: REST API, Ollama LLM, React UI, MCP client |
| `finance-mcp` | 8082 | Finance Skill: `calculate_simple_interest` |
| `research-mcp` | 8083 | Research Skill: `search_learning_resources` |
| `comms-mcp` | 8084 | Communication Skill: `send_greeting` |
| `general-purpose-mcp` | 8081 | Legacy — superseded by the three skill servers above |

---

## Prerequisites

- **Java 21** and **Maven**
- **Ollama** running locally with a tool-calling-capable model:

  ```bash
  ollama pull llama3.1:8b
  ```

  `qwen2.5:7b` and `mistral` also work. Update `mcp-agent/src/main/resources/application.yml`
  if you use a different model name.

- **Node 20+** (only needed if running the React dev server separately — `mvn package`
  installs its own Node via `frontend-maven-plugin`)

---

## Run

Start each process in its own terminal. Skill servers must be up **before** `mcp-agent`.

```bash
# Terminal 1
cd finance-mcp && mvn spring-boot:run

# Terminal 2
cd research-mcp && mvn spring-boot:run

# Terminal 3
cd comms-mcp && mvn spring-boot:run

# Terminal 4 — after all three skill servers are ready
cd mcp-agent && mvn spring-boot:run
```

Open **http://localhost:8080** in a browser.

---

## Try it

**Browser UI** — open http://localhost:8080 and type a prompt. The tools panel on
the left shows all discovered tools grouped by skill server.

**curl — LLM orchestration (chains two tools automatically):**
```bash
curl -s -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"prompt": "Calculate simple interest on 10000 at 7.5% for 3 years"}' | jq
```

**curl — cross-skill prompt (greeting + calculation in one request):**
```bash
curl -s -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"prompt": "Greet Priya and calculate simple interest on 5000 at 6% for 2 years"}' | jq
```

**curl — deterministic tool discovery (no LLM):**
```bash
curl -s http://localhost:8080/api/agent/tools | jq
```

**curl — poke a skill server directly:**
```bash
curl -s -X POST http://localhost:8082/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}' | jq
```

---

## Frontend development (hot reload)

`mcp-agent` must be running on `:8080`. The Vite dev server proxies `/api` to it.

```bash
cd mcp-agent/src/main/frontend
npm install
npm run dev    # → http://localhost:5173
```

**Production build** (bundled into Spring Boot, served at `/`):
```bash
cd mcp-agent && mvn package
# output → target/classes/static/
```

---

## Adding a new skill

1. Create a new Spring Boot module (copy any skill module as template).
2. Annotate tool methods with `@McpTool` / `@McpToolParam`.
3. Add one entry to `mcp-agent/src/main/resources/application.yml`:

```yaml
spring.ai.mcp.client.streamable-http.connections:
  my-new-skill:
    url: http://localhost:8085
    endpoint: /mcp
```

No changes to `mcp-agent` Java code. Tools are discovered automatically at startup.
