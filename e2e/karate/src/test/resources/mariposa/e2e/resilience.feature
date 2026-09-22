Feature: transient failures are retried, exhausted or permanent ones end as TECHNICAL_FAILURE

  Background:
    * def orderEvent = read('common/order-event.js')
    * def publishOrder = read('common/publish-order.feature')
    * def waitOrder = read('common/wait-order.feature')

  Scenario: a product that fails twice with 503 is approved after retries
    * def items = [{ productId: 'PRD-012', quantity: 20, unitPrice: 120.0 }]
    * def orderId = 'ORD-MX-E2ER' + runId + 'RT1'
    * def event = orderEvent({ orderId: orderId, items: items })
    * call publishOrder { event: '#(event)' }
    * call waitOrder { orderId: '#(orderId)', expectedStatus: 'APPROVED' }

  Scenario: a client that always answers 503 ends in TECHNICAL_FAILURE and in the DLT
    * def items = [{ productId: 'PRD-001', quantity: 5, unitPrice: 35.5 }]
    * def orderId = 'ORD-MX-E2ER' + runId + 'TF1'
    * def event = orderEvent({ orderId: orderId, clientId: 'CLI-40002', items: items })
    * def dltMark = kafka.mark(topics.dlt)
    * def processedMark = kafka.mark(topics.processed)
    * call publishOrder { event: '#(event)' }
    * def failureWait = { orderId: '#(orderId)', expectedStatus: 'TECHNICAL_FAILURE', retries: 120 }
    * def result = call waitOrder failureWait
    And match result.order.failure.category == 'EXTERNAL_TRANSIENT'
    * def dead = kafka.readAfter(dltMark, orderId, 1, waits.eventMillis)
    And match dead[0].headers['x-error-category'] == 'EXTERNAL_TRANSIENT'
    And match dead[0].headers['x-event-id'] == event.eventId
    * def published = kafka.readAfter(processedMark, orderId, 1, waits.quietMillis)
    And match published == '#[0]'

  Scenario: a permanent 400 from a dependency is not retried and ends in TECHNICAL_FAILURE
    * def items = [{ productId: 'PRD-014', quantity: 2, unitPrice: 11.0 }]
    * def orderId = 'ORD-PE-E2ER' + runId + 'PF1'
    * def fields = { market: 'PE', currency: 'PEN', clientId: 'CLI-30001' }
    * def event = orderEvent(karate.merge(fields, { orderId: orderId, items: items }))
    * def dltMark = kafka.mark(topics.dlt)
    * call publishOrder { event: '#(event)' }
    * def failureWait = { orderId: '#(orderId)', expectedStatus: 'TECHNICAL_FAILURE', retries: 30 }
    * def result = call waitOrder failureWait
    And match result.order.failure.category == 'EXTERNAL_PERMANENT'
    * def dead = kafka.readAfter(dltMark, orderId, 1, waits.eventMillis)
    And match dead[0].headers['x-error-category'] == 'EXTERNAL_PERMANENT'
