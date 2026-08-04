Feature: Agent Chat API — POST /api/agent/chat

  Background:
    * url baseUrl
    * def chatPath = '/api/agent/chat'

  # ── 200 success ────────────────────────────────────────────────────────

  Scenario: Valid prompt returns 200 with non-empty answer
    Given path chatPath
    And request { prompt: 'Calculate simple interest on 10000 at 7.5% for 3 years' }
    When method POST
    Then status 200
    And match response == { answer: '#string' }
    And match response.answer == '#notempty'

  Scenario: Greeting prompt returns 200 with non-empty answer
    Given path chatPath
    And request { prompt: 'Greet Priya' }
    When method POST
    Then status 200
    And match response == { answer: '#string' }

  Scenario: Multi-tool prompt returns 200
    Given path chatPath
    And request { prompt: 'Greet Alice and calculate SI on 5000 at 6% for 2 years' }
    When method POST
    Then status 200
    And match response.answer == '#notempty'

  Scenario: Response contains only the answer field
    Given path chatPath
    And request { prompt: 'Hello' }
    When method POST
    Then status 200
    And match response == { answer: '#string' }

  # ── 400 validation ──────────────────────────────────────────────────────

  Scenario: Blank prompt returns 400 with error message
    Given path chatPath
    And request { prompt: '   ' }
    When method POST
    Then status 400
    And match response == { answer: 'prompt must not be blank' }

  Scenario: Empty string prompt returns 400
    Given path chatPath
    And request { prompt: '' }
    When method POST
    Then status 400
    And match response.answer == 'prompt must not be blank'

  Scenario: Null prompt field returns 400
    Given path chatPath
    And request { prompt: null }
    When method POST
    Then status 400
    And match response.answer == 'prompt must not be blank'

  Scenario: Missing prompt field returns 400
    Given path chatPath
    And request {}
    When method POST
    Then status 400

  # ── content type ────────────────────────────────────────────────────────

  Scenario: Request must have JSON content type — plain text body returns 400
    Given path chatPath
    And header Content-Type = 'text/plain'
    And request 'Calculate simple interest'
    When method POST
    Then status 415

  Scenario: Response content type is application/json
    Given path chatPath
    And request { prompt: 'Hello' }
    When method POST
    Then status 200
    And match header Content-Type contains 'application/json'
