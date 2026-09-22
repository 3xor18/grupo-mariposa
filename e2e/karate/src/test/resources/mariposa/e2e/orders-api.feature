Feature: orders query API security, pagination and errors

  Background:
    * url ordersUrl
    * def orderEvent = read('common/order-event.js')
    * def publishOrder = read('common/publish-order.feature')
    * def waitOrder = read('common/wait-order.feature')

  Scenario: lists orders newest first with filters and pagination
    * def orderId = 'ORD-MX-E2EQ' + runId + 'LST'
    * def event = orderEvent({ orderId: orderId })
    * call publishOrder { event: '#(event)' }
    * call waitOrder { orderId: '#(orderId)', expectedStatus: 'APPROVED' }
    Given path 'orders'
    And params { status: 'APPROVED', market: 'MX', page: 0, size: 5 }
    And header Authorization = 'Bearer ' + tokens.analyst
    When method get
    Then status 200
    And match response contains { items: '#array', page: 0, size: 5 }
    And match response contains { totalElements: '#number', totalPages: '#number' }
    And match response.items == '#[_ > 0]'
    And match each response.items contains { status: 'APPROVED', market: 'MX' }

  Scenario: unknown order returns the shared problem contract
    Given path 'orders', 'ORD-XX-404'
    And header Authorization = 'Bearer ' + tokens.analyst
    When method get
    Then status 404
    And match response contains { status: 404, code: 'ORDER_NOT_FOUND', traceId: '#string' }

  Scenario: page size above the maximum is rejected
    Given path 'orders'
    And params { size: 1000 }
    And header Authorization = 'Bearer ' + tokens.analyst
    When method get
    Then status 400
    And match response.code == 'VALIDATION_ERROR'

  Scenario: requires a token and the orders-reader role
    Given path 'orders'
    When method get
    Then status 401
    Given path 'orders'
    And header Authorization = 'Bearer ' + tokens.viewer
    When method get
    Then status 403
