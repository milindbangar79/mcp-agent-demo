#!/usr/bin/env bash
# Generates a realistic mix of traffic so the Grafana dashboard has something to show:
#   - LLM chats that fan out to employee-agent, employee-mcp, finance-mcp and comms-mcp
#   - wrong-key calls to employee-agent (401s)
#   - tool errors (unknown employee)
#   - two bursts above the 100 RPS limit on employee-mcp (rate_limited)
# Usage: observability/load/generate-load.sh [rounds]      (default 8 rounds, ~35 s each)
# Override ports if needed: AGENT_URL, EMPLOYEE_AGENT_URL, EMPLOYEE_MCP_URL
set -u
ROUNDS="${1:-8}"
AGENT_URL="${AGENT_URL:-http://localhost:8080}"
EMPLOYEE_AGENT_URL="${EMPLOYEE_AGENT_URL:-http://localhost:8086}"
EMPLOYEE_MCP_URL="${EMPLOYEE_MCP_URL:-http://localhost:8085}"
H='Content-Type: application/json'; A='Accept: application/json, text/event-stream'

chat() { curl -s -m 170 -H "$H" "$AGENT_URL/api/agent/chat" -d "{\"prompt\":\"$1\"}" -o /dev/null; }
mcp()  { curl -s -o /dev/null -H "$H" -H "$A" -H "Mcp-Session-Id: $SID" "$EMPLOYEE_MCP_URL/mcp" -d "$1"; }

SID=$(curl -s -i -H "$H" -H "$A" "$EMPLOYEE_MCP_URL/mcp" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"load","version":"1"}}}' \
  | grep -i mcp-session-id | awk '{print $2}' | tr -d '\r')
mcp '{"jsonrpc":"2.0","method":"notifications/initialized"}'

for i in $(seq 1 "$ROUNDS"); do
  echo "round $i/$ROUNDS"
  chat "Delegate to the employee agent: list the employees in India" &
  chat "Get the details of employee E00$((i % 8 + 1)) using your tools" &
  chat "Calculate simple interest on 10000 at 7.5% for $i years" &
  curl -s -o /dev/null -H "$H" -H "X-API-Key: wrong" "$EMPLOYEE_AGENT_URL/api/employee/ask" -d '{"question":"x"}'
  mcp '{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"get_employee_details","arguments":{"employeeId":"E999"}}}'
  if [ "$i" = 4 ] || [ "$i" = 7 ]; then
    seq 1 250 | xargs -P 30 -I{} curl -s -o /dev/null -H "$H" -H "$A" -H "Mcp-Session-Id: $SID" "$EMPLOYEE_MCP_URL/mcp" \
      -d '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"get_employee_details","arguments":{"employeeId":"E001"}}}'
  fi
  wait
  sleep 20
done
echo "done"
