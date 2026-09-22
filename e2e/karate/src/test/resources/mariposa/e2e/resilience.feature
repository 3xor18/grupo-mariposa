Feature: transient failures are retried; exhausted or permanent failures end as TECHNICAL_FAILURE

  Background:
    * def orderEvent = read('common/order-event.js')

  Scenario: a product that fails twice with 503 is approved after retries
    * def items = [{ productId: 'PRD-012', quantity: 20, unitPrice: 120.0 }]
    * def event = orderEvent({ orderId: 'ORD-MX-E2ER' + runId + 'RT1', items: items })
    * call read('common/publish-order.feature') { event: '#(event)' }
    * call read('common/wait-order.feature') { orderId: '#(event.orderId)', expectedStatus: 'APPROVED' }

  Scenario: a client that always answers 503 ends in TECHNICAL_FAILURE and in the DLT
    * def items = [{ productId: 'PRD-001', quantity: 5, unitPrice: 35.5 }]
    * def event = orderEvent({ orderId: 'ORD-MX-E2ER' + runId + 'TF1', clientId: 'CLI-40002', items: items })
    * def dltMark = kafka.mark(topics.dlt)
    * def processedMark = kafka.mark(topics.processed)
    * call read('common/publish-order.feature') { event: '#(event)' }
    * def result = call read('common/wait-order.feature') { orderId: '#(event.orderId)', expectedStatus: 'TECHNICAL_FAILURE', retries: 120 }
    And match result.order.failure.category == 'EXTERNAL_TRANSIENT'
    * def dead = kafka.readAfter(dltMark, event.orderId, 1, waits.eventMillis)
    And match dead[0].headers contains { 'x-error-category': 'EXTERNAL_TRANSIENT', 'x-event-id': '#(event.eventId)' }
    * def published = kafka.readAfter(processedMark, event.orderId, 1, waits.quietMillis)
    And match published == '#[0]'

  Scenario: a permanent 400 from a dependency is not retried and ends in TECHNICAL_FAILURE
    * def items = [{ productId: 'PRD-014', quantity: 2, unitPrice: 11.0 }]
    * def event = orderEvent({ orderId: 'ORD-PE-E2ER' + runId + 'PF1', market: 'PE', currency: 'PEN', clientId: 'CLI-30001', items: items })
    * def dltMark = kafka.mark(topics.dlt)
    * call read('common/publish-order.feature') { event: '#(event)' }
    * def result = call read('common/wait-order.feature') { orderId: '#(event.orderId)', expectedStatus: 'TECHNICAL_FAILURE', retries: 30 }
    And match result.order.failure.category == 'EXTERNAL_PERMANENT'
    * def dead = kafka.readAfter(dltMark, event.orderId, 1, waits.eventMillis)
    And match dead[0].headers['x-error-category'] == 'EXTERNAL_PERMANENT'
