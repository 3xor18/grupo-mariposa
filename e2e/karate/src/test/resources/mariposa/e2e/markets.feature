Feature: configurable market catalog (ADR 0006): currency decimals, shared currency, unknown market

  Background:
    * def orderEvent = read('common/order-event.js')
    * def publishOrder = read('common/publish-order.feature')
    * def waitOrder = read('common/wait-order.feature')
    * def newOrderId = function(market, id) { return 'ORD-' + market + '-E2EM' + runId + id }

  Scenario: Chile rounds every amount to whole pesos (CLP has no decimals)
    * def items =
      """
      [
        { productId: 'PRD-015', quantity: 24, unitPrice: 1990 },
        { productId: 'PRD-016', quantity: 10, unitPrice: 1290 }
      ]
      """
    * def orderId = newOrderId('CL', 'A1')
    * def fields = { market: 'CL', currency: 'CLP', clientId: 'CLI-50001' }
    * def event = orderEvent(karate.merge(fields, { orderId: orderId, items: items }))
    * call publishOrder { event: '#(event)' }
    * def result = call waitOrder { orderId: '#(orderId)', expectedStatus: 'APPROVED' }
    And match result.order.currency == 'CLP'
    And match result.order.totals ==
      """
      {
        grossSubtotal: 60660,
        discount: 1433,
        netSubtotal: 59227,
        tax: 11253,
        grandTotal: 70480
      }
      """

  Scenario: Ecuador uses the shared USD currency with two decimals
    * def items =
      """
      [
        { productId: 'PRD-018', quantity: 30, unitPrice: 1.25 },
        { productId: 'PRD-019', quantity: 5, unitPrice: 8.4 }
      ]
      """
    * def orderId = newOrderId('EC', 'A1')
    * def fields = { market: 'EC', currency: 'USD', clientId: 'CLI-60001' }
    * def event = orderEvent(karate.merge(fields, { orderId: orderId, items: items }))
    * call publishOrder { event: '#(event)' }
    * def result = call waitOrder { orderId: '#(orderId)', expectedStatus: 'APPROVED' }
    And match result.order.currency == 'USD'
    And match result.order.totals ==
      """
      {
        grossSubtotal: 79.50,
        discount: 1.13,
        netSubtotal: 78.37,
        tax: 2.10,
        grandTotal: 80.47
      }
      """

  Scenario: a market outside the catalog is a VALIDATION error in the DLT, never an order
    * def orderId = newOrderId('AR', 'V1')
    * def items = [{ productId: 'PRD-001', quantity: 5, unitPrice: 1500 }]
    * def event = orderEvent({ orderId: orderId, market: 'AR', currency: 'ARS', items: items })
    * def dltMark = kafka.mark(topics.dlt)
    * call publishOrder { event: '#(event)' }
    * def dead = kafka.readAfter(dltMark, orderId, 1, waits.eventMillis)
    And match dead == '#[1]'
    And match dead[0].headers['x-error-category'] == 'VALIDATION'
    And match dead[0].headers['x-event-id'] == event.eventId
    Given url ordersUrl + '/orders/' + orderId
    And header Authorization = 'Bearer ' + tokens.analyst
    When method get
    Then status 404
