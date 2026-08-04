Feature: Agent Tools Discovery API — GET /api/agent/tools

  Background:
    * url baseUrl
    * def toolsPath = '/api/agent/tools'

  # ── structure ────────────────────────────────────────────────────────────

  Scenario: Returns 200 with a JSON array
    Given path toolsPath
    When method GET
    Then status 200
    And match response == '#[]'

  Scenario: Each tool entry has server, name, and description fields
    Given path toolsPath
    When method GET
    Then status 200
    And match each response == { server: '#string', name: '#string', description: '#string' }

  Scenario: Each tool entry has non-empty field values
    Given path toolsPath
    When method GET
    Then status 200
    And match each response ==
    """
    {
      server:      '#notempty',
      name:        '#notempty',
      description: '#notempty'
    }
    """

  # ── skill server coverage ────────────────────────────────────────────────

  Scenario: Response includes tools from all three skill servers
    Given path toolsPath
    When method GET
    Then status 200
    * def servers = $response[*].server
    And match servers contains 'finance-mcp'
    And match servers contains 'research-mcp'
    And match servers contains 'comms-mcp'

  Scenario: Response includes all expected tool names
    Given path toolsPath
    When method GET
    Then status 200
    * def names = $response[*].name
    And match names contains 'calculate_simple_interest'
    And match names contains 'search_learning_resources'
    And match names contains 'send_greeting'

  Scenario: Total number of discovered tools is three
    Given path toolsPath
    When method GET
    Then status 200
    And assert response.length == 3

  # ── http behaviour ───────────────────────────────────────────────────────

  Scenario: Endpoint accepts GET without a request body
    Given path toolsPath
    When method GET
    Then status 200

  Scenario: Response content type is application/json
    Given path toolsPath
    When method GET
    Then status 200
    And match header Content-Type contains 'application/json'

  Scenario: POST to tools endpoint returns 405 Method Not Allowed
    Given path toolsPath
    And request {}
    When method POST
    Then status 405
