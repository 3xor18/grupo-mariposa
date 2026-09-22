Feature: invalid messages never become processed orders and land in the DLT with metadata

  Background:
    * def orderEvent = read('common/order-event.js')
    * def publishOrder = read('common/publish-order.feature')
    * def line = function(q, p) { return { productId: 'PRD-001', quantity: q, unitPrice: p } }
    * def invalidItems =
      """
      {
        single: '#([line(1, 1.0)])',
        duplicated: '#([line(1, 1.0), line(2, 1.0)])',
        zeroQuantity: '#([line(0, 1.0)])',
        negativePrice: '#([line(1, -0.01)])',
        empty: []
      }
      """

  Scenario Outline: <case> goes to the DLT with the original payload
    * def orderId = 'ORD-MX-E2EV' + runId + '<suffix>'
    * def items = invalidItems['<items>']
    * def event = orderEvent({ orderId: orderId, currency: '<currency>', items: items })
    * def dltMark = kafka.mark(topics.dlt)
    * call publishOrder { event: '#(event)' }
    * def dead = kafka.readAfter(dltMark, orderId, 1, waits.eventMillis)
    And match dead == '#[1]'
    * def headers = dead[0].headers
    And match headers['x-error-category'] == 'VALIDATION'
    And match headers['x-component'] == 'order-processor'
    And match headers contains { 'x-order-id': '#(orderId)', 'x-event-id': '#(event.eventId)' }
    And match headers contains { 'x-attempts': '#string', 'x-failed-at': '#string' }
    And match headers contains { 'x-error-cause': '#string' }
    And match karate.fromString(dead[0].value) == event
    Given url ordersUrl + '/orders/' + orderId
    And header Authorization = 'Bearer ' + tokens.analyst
    When method get
    Then status 404

    Examples:
      | case                | suffix | currency | items         |
      | currency mismatch   | 1      | COP      | single        |
      | duplicated product  | 2      | MXN      | duplicated    |
      | zero quantity       | 3      | MXN      | zeroQuantity  |
      | negative unit price | 4      | MXN      | negativePrice |
      | empty items         | 5      | MXN      | empty         |

  Scenario: unreadable payload is preserved byte by byte in the DLT
    * def key = 'ORD-MX-E2EV' + runId + 'RAW'
    * def raw = '{"orderId":"' + key + '", this is not json'
    * def dltMark = kafka.mark(topics.dlt)
    * eval kafka.publish(topics.created, key, raw)
    * def dead = kafka.readAfter(dltMark, key, 1, waits.eventMillis)
    And match dead == '#[1]'
    And match dead[0].value == raw
    And match dead[0].headers['x-error-category'] == 'DESERIALIZATION'
