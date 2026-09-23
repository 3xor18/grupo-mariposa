Feature: clients-api contract, validation, errors and security

  Background:
    * url clientsUrl
    * def problem = read('common/problem.json')

  Scenario: returns a client
    Given path 'clients', 'CLI-99821'
    And header Authorization = 'Bearer ' + tokens.admin
    When method get
    Then status 200
    And match response ==
      """
      {
        clientId: 'CLI-99821',
        name: 'Distribuidora Central',
        status: 'ACTIVE',
        segment: 'WHOLESALE',
        taxRegime: 'GENERAL',
        market: 'MX'
      }
      """

  Scenario: returns 404 with the shared problem contract
    Given path 'clients', 'CLI-00000'
    And header Authorization = 'Bearer ' + tokens.admin
    When method get
    Then status 404
    And match response == problem
    And match response.code == 'CLIENT_NOT_FOUND'

  Scenario: rejects a malformed client id
    Given path 'clients', 'client-1'
    And header Authorization = 'Bearer ' + tokens.admin
    When method get
    Then status 400
    And match response.code == 'VALIDATION_ERROR'

  Scenario: requires a token and the clients-reader role
    Given path 'clients', 'CLI-99821'
    When method get
    Then status 401
    Given path 'clients', 'CLI-99821'
    And header Authorization = 'Bearer ' + tokens.viewer
    When method get
    Then status 403
