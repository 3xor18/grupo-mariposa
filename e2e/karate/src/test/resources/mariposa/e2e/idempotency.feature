Feature: duplicates, concurrent deliveries, version conflicts and stale versions

  Background:
    * def orderEvent = read('common/order-event.js')
    * def publishOrder = read('common/publish-order.feature')
    * def waitOrder = read('common/wait-order.feature')
    * def sourceEventId = function(record) { return karate.fromString(record.value).sourceEventId }
    * def sourceEventIds = function(records) { return karate.map(records, sourceEventId) }
    * def line = function(q, p) { return [{ productId: 'PRD-001', quantity: q, unitPrice: p }] }

  Scenario: the same eventId delivered many times produces exactly one effect
    * def orderId = 'ORD-MX-E2EI' + runId + 'DUP'
    * def event = orderEvent({ orderId: orderId })
    * string payload = event
    * def processedMark = kafka.mark(topics.processed)
    * def deliveries = 10
    * def publishOnce = function() { kafka.publish(topics.created, orderId, payload) }
    * eval karate.repeat(deliveries, publishOnce)
    * call waitOrder { orderId: '#(orderId)', expectedStatus: 'APPROVED' }
    * def duplicateProbe = 2
    * def published = kafka.readAfter(processedMark, orderId, duplicateProbe, waits.quietMillis)
    And match published == '#[1]'

  Scenario: a different event with the same orderId and version is a conflict and the first wins
    * def orderId = 'ORD-MX-E2EI' + runId + 'CON'
    * def first = orderEvent({ orderId: orderId })
    * call publishOrder { event: '#(first)' }
    * call waitOrder { orderId: '#(orderId)', expectedStatus: 'APPROVED' }
    * def second = orderEvent({ orderId: orderId, items: line(1, 1.0) })
    * def dltMark = kafka.mark(topics.dlt)
    * call publishOrder { event: '#(second)' }
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
    * def v2 = orderEvent({ orderId: orderId, eventVersion: 2, items: line(48, 35.5) })
    * def stale = orderEvent({ orderId: orderId, eventVersion: 1, items: line(999, 1.0) })
    * def sentinel = orderEvent({ orderId: orderId, eventVersion: 3, items: line(30, 35.5) })
    * def processedMark = kafka.mark(topics.processed)
    * call publishOrder { event: '#(v1)' }
    * call waitOrder { orderId: '#(orderId)', expectedStatus: 'APPROVED' }
    * call publishOrder { event: '#(v2)' }
    * def v2Wait = { orderId: '#(orderId)', expectedStatus: 'APPROVED', expectedVersion: 2 }
    * def afterV2 = call waitOrder v2Wait
    And match afterV2.order.sourceEventId == v2.eventId
    * call publishOrder { event: '#(stale)' }
    * call publishOrder { event: '#(sentinel)' }
    * def sentinelWait = { orderId: '#(orderId)', expectedStatus: 'APPROVED', expectedVersion: 3 }
    * def afterSentinel = call waitOrder sentinelWait
    And match afterSentinel.order.sourceEventId == sentinel.eventId
    * def published = kafka.readAfter(processedMark, orderId, 3, waits.eventMillis)
    * def expectedIds = ['#(v1.eventId)', '#(v2.eventId)', '#(sentinel.eventId)']
    And match sourceEventIds(published) contains only expectedIds
