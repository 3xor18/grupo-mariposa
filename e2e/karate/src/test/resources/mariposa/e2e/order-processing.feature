Feature: order-processor business outcomes through Kafka, MongoDB and the query API

  Background:
    * def orderEvent = read('common/order-event.js')
    * def publishOrder = read('common/publish-order.feature')
    * def waitOrder = read('common/wait-order.feature')
    * def newOrderId = function(market, suffix) { return 'ORD-' + market + '-E2E' + runId + suffix }

  Scenario: golden example is approved with the totals of the contract
    * def event = orderEvent({ orderId: newOrderId('MX', 'A1') })
    * def processedMark = kafka.mark(topics.processed)
    * call publishOrder { event: '#(event)' }
    * def result = call waitOrder { orderId: '#(event.orderId)', expectedStatus: 'APPROVED' }
    * def order = result.order
    And match order.totals ==
      """
      {
        grossSubtotal: 1836.00,
        discount: 25.56,
        netSubtotal: 1810.44,
        tax: 289.67,
        grandTotal: 2100.11
      }
      """
    And match order.sourceEventId == event.eventId
    And match order.client.clientId == 'CLI-99821'
    And match order.client.name == 'Distribuidora Central'
    And match order.lines[0] contains { productId: 'PRD-001', quantity: 24, discount: 25.56 }
    And match order.reason == null
    * def published = kafka.readAfter(processedMark, event.orderId, 1, waits.eventMillis)
    And match published == '#[1]'
    * def outEvent = karate.fromString(published[0].value)
    And match outEvent contains
      """
      {
        sourceEventId: '#(event.eventId)',
        orderId: '#(event.orderId)',
        status: 'APPROVED',
        market: 'MX',
        currency: 'MXN',
        reason: null
      }
      """
    And match outEvent.totals.grandTotal == 2100.11

  Scenario: exempt client pays no tax and wholesale discount applies from 20 units
    * def items =
      """
      [
        { productId: 'PRD-001', quantity: 20, unitPrice: 10.0 },
        { productId: 'PRD-003', quantity: 19, unitPrice: 10.0 }
      ]
      """
    * def orderId = newOrderId('MX', 'A2')
    * def event = orderEvent({ orderId: orderId, clientId: 'CLI-10003', items: items })
    * call publishOrder { event: '#(event)' }
    * def result = call waitOrder { orderId: '#(orderId)', expectedStatus: 'APPROVED' }
    And match result.order.totals ==
      """
      { grossSubtotal: 390.00, discount: 6.00, netSubtotal: 384.00, tax: 0.00, grandTotal: 384.00 }
      """

  Scenario Outline: market specific tax rates (<market>)
    * def items =
      """
      [
        { productId: '<standard>', quantity: 1, unitPrice: 100.0 },
        { productId: '<reduced>', quantity: 1, unitPrice: 100.0 }
      ]
      """
    * def orderId = newOrderId('<market>', 'T')
    * def fields = { market: '<market>', currency: '<currency>', clientId: '<client>' }
    * def event = orderEvent(karate.merge(fields, { orderId: orderId, items: items }))
    * call publishOrder { event: '#(event)' }
    * def result = call waitOrder { orderId: '#(orderId)', expectedStatus: 'APPROVED' }
    And match result.order.totals.tax == <tax>

    Examples:
      | market | currency | client    | standard | reduced | tax   |
      | MX     | MXN      | CLI-99821 | PRD-001  | PRD-003 | 24.00 |
      | CO     | COP      | CLI-20001 | PRD-006  | PRD-005 | 24.00 |
      | PE     | PEN      | CLI-30001 | PRD-011  | PRD-010 | 28.00 |

  Scenario Outline: business rejection is explicit and traceable (<reason>)
    * def items = [{ productId: '<product>', quantity: 5, unitPrice: 10.0 }]
    * def orderId = newOrderId('<market>', '<suffix>')
    * def fields = { market: '<market>', currency: '<currency>', clientId: '<client>' }
    * def event = orderEvent(karate.merge(fields, { orderId: orderId, items: items }))
    * def processedMark = kafka.mark(topics.processed)
    * call publishOrder { event: '#(event)' }
    * def result = call waitOrder { orderId: '#(orderId)', expectedStatus: 'REJECTED' }
    And match result.order.reason == '<reason>'
    And match result.order.violations[0].code == '<reason>'
    * def published = kafka.readAfter(processedMark, orderId, 1, waits.eventMillis)
    And match published == '#[1]'
    * def outEvent = karate.fromString(published[0].value)
    And match outEvent contains { status: 'REJECTED', reason: '<reason>' }

    Examples:
      | suffix | market | currency | client    | product | reason                 |
      | R1     | CO     | COP      | CLI-20002 | PRD-001 | CLIENT_NOT_ACTIVE      |
      | R2     | PE     | PEN      | CLI-99821 | PRD-001 | CLIENT_MARKET_MISMATCH |
      | R3     | MX     | MXN      | CLI-10002 | PRD-004 | PRODUCT_NOT_ACTIVE     |
      | R4     | PE     | PEN      | CLI-30002 | PRD-008 | PRODUCT_NOT_FOUND      |
      | R5     | MX     | MXN      | CLI-77777 | PRD-001 | CLIENT_NOT_FOUND       |
