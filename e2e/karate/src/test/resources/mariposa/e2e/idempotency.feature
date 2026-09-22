Feature: duplicates, concurrent deliveries, version conflicts and stale versions

  Background:
    * def orderEvent = read('common/order-event.js')
    * def sourceEventIds = function(records) { return karate.map(records, function(r) { return karate.fromString(r.value).sourceEventId }) }

  Scenario: the same eventId delivered many times produces exactly one effect
    * def event = orderEvent({ orderId: 'ORD-MX-E2EI' + runId + 'DUP' })
    * string payload = event
    * def processedMark = kafka.mark(topics.processed)
    * def publishMany = function(n) { for (var i = 0; i < n; i++) kafka.publish(topics.created, event.orderId, payload) }
    * eval publishMany(10)
    * call read('common/wait-order.feature') { orderId: '#(event.orderId)', expectedStatus: 'APPROVED' }
    * def oneMoreThanExpected = 2
    * def published = kafka.readAfter(processedMark, event.orderId, oneMoreThanExpected, waits.quietMillis)
    And match published == '#[1]'

  Scenario: a different event with the same orderId and eventVersion is a conflict and the first wins
    * def orderId = 'ORD-MX-E2EI' + runId + 'CON'
    * def first = orderEvent({ orderId: orderId })
    * call read('common/publish-order.feature') { event: '#(first)' }
    * call read('common/wait-order.feature') { orderId: '#(orderId)', expectedStatus: 'APPROVED' }
    * def second = orderEvent({ orderId: orderId, items: [{ productId: 'PRD-001', quantity: 1, unitPrice: 1.0 }] })
    * def dltMark = kafka.mark(topics.dlt)
    * call read('common/publish-order.feature') { event: '#(second)' }
    * def dead = kafka.readAfter(dltMark, orderId, 1, waits.eventMillis)
    And match dead == '#[1]'
    And match dead[0].headers['x-error-category'] == 'VERSION_CONFLICT'
    And match dead[0].headers['x-event-id'] == second.eventId
    Given url ordersUrl + '/orders/' + orderId
    And header Authorization = 'Bearer ' + tokens.analyst
    When method get
    Then status 200
    And match response.sourceEventId == first.eventId
    And match response.totals.grandTotal == 2100.11

  Scenario: a newer version supersedes and a later stale version is ignored
    * def orderId = 'ORD-MX-E2EI' + runId + 'VER'
    * def v1 = orderEvent({ orderId: orderId })
    * def v2 = orderEvent({ orderId: orderId, eventVersion: 2, items: [{ productId: 'PRD-001', quantity: 48, unitPrice: 35.5 }] })
    * def stale = orderEvent({ orderId: orderId, eventVersion: 1, items: [{ productId: 'PRD-001', quantity: 999, unitPrice: 1.0 }] })
    * def sentinel = orderEvent({ orderId: orderId, eventVersion: 3, items: [{ productId: 'PRD-001', quantity: 30, unitPrice: 35.5 }] })
    * def processedMark = kafka.mark(topics.processed)
    * call read('common/publish-order.feature') { event: '#(v1)' }
    * call read('common/wait-order.feature') { orderId: '#(orderId)', expectedStatus: 'APPROVED' }
    * call read('common/publish-order.feature') { event: '#(v2)' }
    * def afterV2 = call read('common/wait-order.feature') { orderId: '#(orderId)', expectedStatus: 'APPROVED', expectedVersion: 2 }
    And match afterV2.order.sourceEventId == v2.eventId
    * call read('common/publish-order.feature') { event: '#(stale)' }
    * call read('common/publish-order.feature') { event: '#(sentinel)' }
    * def afterSentinel = call read('common/wait-order.feature') { orderId: '#(orderId)', expectedStatus: 'APPROVED', expectedVersion: 3 }
    And match afterSentinel.order.sourceEventId == sentinel.eventId
    * def published = kafka.readAfter(processedMark, orderId, 3, waits.eventMillis)
    And match sourceEventIds(published) contains only ['#(v1.eventId)', '#(v2.eventId)', '#(sentinel.eventId)']
