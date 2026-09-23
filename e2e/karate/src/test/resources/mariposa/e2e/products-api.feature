Feature: products-api contract, validation, errors and security

  Background:
    * url productsUrl
    * def problem = read('common/problem.json')

  Scenario: returns a product available in the requested market
    Given path 'products', 'PRD-001'
    And param market = 'MX'
    And header Authorization = 'Bearer ' + tokens.admin
    When method get
    Then status 200
    And match response ==
      """
      {
        productId: 'PRD-001',
        name: 'Bebida 600 ml',
        sku: 'BEB-600-PET',
        status: 'ACTIVE',
        taxCategory: 'STANDARD'
      }
      """

  Scenario: returns 404 when the product exists but not in that market
    Given path 'products', 'PRD-008'
    And param market = 'PE'
    And header Authorization = 'Bearer ' + tokens.admin
    When method get
    Then status 404
    And match response == problem
    And match response.code == 'PRODUCT_NOT_FOUND'

  Scenario Outline: rejects invalid input with 400 and field errors (<case>)
    Given path 'products', '<productId>'
    And params <query>
    And header Authorization = 'Bearer ' + tokens.admin
    When method get
    Then status 400
    And match response == problem
    And match response.code == 'VALIDATION_ERROR'
    And match response.errors == '#[_ > 0]'

    Examples:
      | case               | productId | query            |
      | unsupported market | PRD-001   | { market: 'AR' } |
      | missing market     | PRD-001   | {}               |
      | malformed id       | abc$      | { market: 'MX' } |

  Scenario: requires a token
    Given path 'products', 'PRD-001'
    And param market = 'MX'
    When method get
    Then status 401
    And match response.code == 'UNAUTHORIZED'

  Scenario: requires the products-reader role
    Given path 'products', 'PRD-001'
    And param market = 'MX'
    And header Authorization = 'Bearer ' + tokens.analyst
    When method get
    Then status 403
    And match response.code == 'FORBIDDEN'

  Scenario: exposes health without authentication
    Given path 'health', 'ready'
    When method get
    Then status 200
    And match response == { status: 'UP' }
