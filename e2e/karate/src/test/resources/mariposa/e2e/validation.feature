Feature: invalid messages never become processed orders and land in the DLT with metadata

  Background:
    * def orderEvent = read('common/order-event.js')

  Scenario Outline: <case> goes to the DLT with the original payload
    * def orderId = 'ORD-MX-E2EV' + runId + '<suffix>'
    * def event = orderEvent({ orderId: orderId, currency: '<currency>', items: <items> })
    * def dltMark = kafka.mark(topics.dlt)
    * call read('common/publish-order.feature') { event: '#(event)' }
    * def dead = kafka.readAfter(dltMark, orderId, 1, waits.eventMillis)
    And match dead == '#[1]'
    And match dead[0].headers contains { 'x-error-category': 'VALIDATION', 'x-component': 'order-processor' }
    And match dead[0].headers contains { 'x-order-id': '#(orderId)', 'x-event-id': '#(event.eventId)' }
    And match dead[0].headers contains { 'x-attempts': '#string', 'x-failed-at': '#string', 'x-error-cause': '#string' }
    And match karate.fromString(dead[0].value) == event
    Given url ordersUrl + '/orders/' + orderId
    And header Authorization = 'Bearer ' + tokens.analyst
    When method get
    Then status 404

    Examples:
      | case                | suffix | currency | items                                                                                                            |
      | currency mismatch   | 1      | COP      | [{ productId: 'PRD-001', quantity: 1, unitPrice: 1.0 }]                                                          |
      | duplicated product  | 2      | MXN      | [{ productId: 'PRD-001', quantity: 1, unitPrice: 1.0 }, { productId: 'PRD-001', quantity: 2, unitPrice: 1.0 }] |
      | zero quantity       | 3      | MXN      | [{ productId: 'PRD-001', quantity: 0, unitPrice: 1.0 }]                                                          |
      | negative unit price | 4      | MXN      | [{ productId: 'PRD-001', quantity: 1, unitPrice: -0.01 }]                                                        |
      | empty items         | 5      | MXN      | []                                                                                                               |

  Scenario: unreadable payload is preserved byte by byte in the DLT
    * def key = 'ORD-MX-E2EV' + runId + 'RAW'
    * def raw = '{"orderId":"' + key + '", this is not json'
    * def dltMark = kafka.mark(topics.dlt)
    * eval kafka.publish(topics.created, key, raw)
    * def dead = kafka.readAfter(dltMark, key, 1, waits.eventMillis)
    And match dead == '#[1]'
    And match dead[0].value == raw
    And match dead[0].headers['x-error-category'] == 'DESERIALIZATION'
