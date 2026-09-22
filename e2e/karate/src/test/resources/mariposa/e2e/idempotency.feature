Feature: duplicates, concurrent deliveries, version conflicts and stale versions

  Background:
    * def analyst = call read('common/token.feature') { username: 'analyst' }
    * def token = analyst.accessToken
    * def orderEvent = read('common/order-event.js')

  Scenario: the same eventId delivered many times produces exactly one effect
    * def event = orderEvent({ orderId: 'ORD-MX-E2EI' + runId + 'DUP' })
    * string payload = event
    * def publishMany = function(n) { for (var i = 0; i < n; i++) kafka.publish(topics.created, event.orderId, payload) }
    * eval publishMany(10)
    * call read('common/wait-order.feature') { orderId: '#(event.orderId)', expectedStatus: 'APPROVED' }
    * def published = kafka.readByKey(topics.processed, event.orderId, 8000)
    And match published == '#[1]'

  Scenario: a different event with the same orderId and eventVersion is a conflict and the first wins
    * def orderId = 'ORD-MX-E2EI' + runId + 'CON'
    * def first = orderEvent({ orderId: orderId })
    * call read('common/publish-order.feature') { event: '#(first)' }
    * call read('common/wait-order.feature') { orderId: '#(orderId)', expectedStatus: 'APPROVED' }
    * def second = orderEvent({ orderId: orderId, items: [{ productId: 'PRD-001', quantity: 1, unitPrice: 1.0 }] })
    * call read('common/publish-order.feature') { event: '#(second)' }
    * def dead = kafka.readByKey(topics.dlt, orderId, 8000)
    And match dead[0].headers['x-error-category'] == 'VERSION_CONFLICT'
    And match dead[0].headers['x-event-id'] == second.eventId
    Given url ordersUrl + '/orders/' + orderId
    And header Authorization = 'Bearer ' + token
    When method get
    Then status 200
    And match response.sourceEventId == first.eventId
    And match response.totals.grandTotal == 2100.11

  Scenario: a newer version supersedes and a later stale version is ignored
    * def orderId = 'ORD-MX-E2EI' + runId + 'VER'
    * def v1 = orderEvent({ orderId: orderId })
    * def v2 = orderEvent({ orderId: orderId, eventVersion: 2, items: [{ productId: 'PRD-001', quantity: 48, unitPrice: 35.5 }] })
    * call read('common/publish-order.feature') { event: '#(v1)' }
    * call read('common/wait-order.feature') { orderId: '#(orderId)', expectedStatus: 'APPROVED' }
    * call read('common/publish-order.feature') { event: '#(v2)' }
    * configure retry = { count: 30, interval: 1000 }
    Given url ordersUrl + '/orders/' + orderId
    And header Authorization = 'Bearer ' + token
    And retry until response.eventVersion == 2
    When method get
    Then status 200
    And match response.sourceEventId == v2.eventId
    * def stale = orderEvent({ orderId: orderId, eventVersion: 1, items: [{ productId: 'PRD-001', quantity: 999, unitPrice: 1.0 }] })
    * call read('common/publish-order.feature') { event: '#(stale)' }
    * eval java.lang.Thread.sleep(4000)
    Given url ordersUrl + '/orders/' + orderId
    And header Authorization = 'Bearer ' + token
    When method get
    Then status 200
    And match response.eventVersion == 2
    And match response.sourceEventId == v2.eventId
    * def published = kafka.readByKey(topics.processed, orderId, 5000)
    And match published == '#[2]'
