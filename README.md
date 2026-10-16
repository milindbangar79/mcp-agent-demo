# MCP Agent Demo — Spring Boot 4.1 + Spring AI 2.0

**GitHub:** https://github.com/milindbangar79/mcp-agent-demo

An LLM-driven agent that discovers and orchestrates tools from **domain-scoped
MCP skill servers and one peer agent**, with a React 19 frontend. The agent uses a local Ollama model —
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
         ├──▶ comms-mcp   (:8084)   Communication Skill
         │      └── send_greeting
         │
         ├──▶ employee-mcp (:8085)  Employee Skill
         │      ├── get_employee_details
         │      └── search_employees
         │
         └──▶ employee-agent (:8086)  Agent-to-agent (REST, X-API-Key)
                └── uses employee-mcp tools internally
```

Employee + finance flow: `mcp-agent` obtains employees (via `employee-agent` or `employee-mcp`
directly), then passes their salaries to `finance-mcp`'s compound-interest tools.

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
| `employee-mcp` | 8085 | Employee Skill: `get_employee_details`, `search_employees` (100 RPS shared limit) |
| `employee-agent` | 8086 | Agent called by mcp-agent (REST, `X-API-Key` secured); uses employee-mcp |
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

# Terminal 3b — employee skill + employee agent (start before mcp-agent)
cd employee-mcp && mvn spring-boot:run
cd employee-agent && mvn spring-boot:run   # AGENT_SHARED_API_KEY must match mcp-agent's

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


---

## Employee agent, employee-mcp, compound interest

### What was added
- **`employee-agent` (:8086)** — a peer agent with its own Ollama `ChatClient` and the `employee-mcp`
  tools. `mcp-agent` calls it through its `ask_employee_agent` tool (`POST /api/employee/ask`).
- **`employee-mcp` (:8085)** — MCP server with two tools (data is in-memory sample data, E001–E008):
  - `get_employee_details(employeeId)`
  - `search_employees(employeeId, dateOfBirth, dobFrom, dobTo, region, country, state)` — all
    filters optional, AND-combined, **at least one required**; dates are `yyyy-MM-dd`.
- **`finance-mcp`** — new `calculate_compound_interest` (one principal) and
  `calculate_compound_interest_batch` (many `{id, principal}` items, same rate/term; max 500).
  Formula `A = P·(1 + r/(100·n))^(n·t)`; `years × compoundsPerYear` must be a whole number.
  Employee salary is the principal; rate, years and compounds/year come from the user prompt.
- **Rate limit** — `employee-mcp` and `finance-mcp` each enforce **one shared limit of 100 calls/sec
  across all their tools** (strict sliding 1s window, `mcp.rate-limit.requests-per-second`).
  Excess calls return a tool error (`isError: true`, "Rate limit exceeded…").

### Security model
| Link | Mechanism |
|---|---|
| mcp-agent → employee-agent | Shared secret in the `X-API-Key` header; checked in constant time on every `/api/**` call. Missing/wrong → **401**. |
| agents → MCP servers | Not authenticated (local demo); protected by the 100 RPS limit only. |

The key is read from `AGENT_SHARED_API_KEY` by **both** `mcp-agent` (`employee-agent.api-key`) and
`employee-agent` (`agent.security.api-key`). The default `dev-only-shared-key` is for local use only.

```bash
export AGENT_SHARED_API_KEY='a-long-random-value'   # set in BOTH terminals before starting
```

### Testing

**Unit tests**
```bash
cd employee-mcp   && mvn test    # search filters, 100 RPS limiter
cd finance-mcp    && mvn test    # compound interest, batch, limiter
cd employee-agent && mvn test    # API-key filter (401 / pass-through)
```

**1. Agent → Agent (mcp-agent → employee-agent) with security**

Start `employee-mcp` and `employee-agent` (Ollama running). Call `employee-agent` directly:
```bash
URL=http://localhost:8086/api/employee/ask
BODY='{"question":"Give me the details of employee E001"}'

# no key  -> 401
curl -s -o /dev/null -w "%{http_code}\n" -X POST $URL -H 'Content-Type: application/json' -d "$BODY"
# wrong key -> 401
curl -s -o /dev/null -w "%{http_code}\n" -X POST $URL -H 'Content-Type: application/json' -H 'X-API-Key: wrong' -d "$BODY"
# correct key -> 200 with the answer
curl -s -X POST $URL -H 'Content-Type: application/json' \
  -H "X-API-Key: ${AGENT_SHARED_API_KEY:-dev-only-shared-key}" -d "$BODY" | jq
```
End to end through `mcp-agent` (uses the key it holds):
```bash
curl -s -X POST http://localhost:8080/api/agent/chat -H 'Content-Type: application/json' \
  -d '{"prompt": "Ask the employee agent for the details of employee E001"}' | jq
```
To see the security fail through `mcp-agent`, start it with a different `AGENT_SHARED_API_KEY` than
`employee-agent`; the delegation call returns 401 and the agent reports the error. (Check
`employee-agent`'s log for `Rejected request … missing or invalid X-API-Key`.)

**2. Agent → MCP (employee-mcp, finance-mcp)**

Deterministic discovery (no LLM) — should list the employee and compound-interest tools:
```bash
curl -s http://localhost:8080/api/agent/tools | jq '.[] | select(.server=="employee-mcp" or .server=="finance-mcp")'
```
LLM-driven, employee data feeding finance:
```bash
# one employee
curl -s -X POST http://localhost:8080/api/agent/chat -H 'Content-Type: application/json' \
  -d '{"prompt": "Compound interest on employee E001 salary at 7% for 3 years, compounded annually"}' | jq
# many employees (dynamic filters)
curl -s -X POST http://localhost:8080/api/agent/chat -H 'Content-Type: application/json' \
  -d '{"prompt": "For all employees in India, compound interest on salary at 7% for 3 years, compounded annually"}' | jq
```
Poke `employee-mcp` directly (Streamable HTTP needs an initialized session):
```bash
H1='Content-Type: application/json'; H2='Accept: application/json, text/event-stream'
curl -s -D /tmp/h -H "$H1" -H "$H2" localhost:8085/mcp -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"t","version":"1"}}}' >/dev/null
S=$(grep -i mcp-session-id /tmp/h | awk '{print $2}' | tr -d '\r')
curl -s -H "$H1" -H "$H2" -H "Mcp-Session-Id: $S" localhost:8085/mcp -d '{"jsonrpc":"2.0","method":"notifications/initialized"}' >/dev/null
curl -s -H "$H1" -H "$H2" -H "Mcp-Session-Id: $S" localhost:8085/mcp \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"search_employees","arguments":{"country":"India"}}}'
```

**3. Rate limit (100 RPS across all tools)**

With the session `$S` from above, fire 300 calls in parallel against `employee-mcp`; within one second
exactly 100 succeed and the rest are rejected:
```bash
seq 1 300 | xargs -P 50 -I{} curl -s -H "$H1" -H "$H2" -H "Mcp-Session-Id: $S" localhost:8085/mcp \
  -d '{"jsonrpc":"2.0","id":{},"method":"tools/call","params":{"name":"get_employee_details","arguments":{"employeeId":"E001"}}}' \
  | grep -o '"isError":[a-z]*' | sort | uniq -c
#  100 "isError":false
#  200 "isError":true      (≈ — depends on how long the burst takes; the cap is per 1s window)
```
Same approach works on `finance-mcp` (:8082) with `calculate_compound_interest`. Change the limit with
`--mcp.rate-limit.requests-per-second=N`.
