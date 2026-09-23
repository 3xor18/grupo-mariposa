Feature: master data changes invalidate the order-processor cache through change events (ADR 0007)

  Background:
    * def getEntity = read('common/get-entity.feature')
    * def patchEntity = read('common/patch-entity.feature')
    * def ensureStatus = read('common/ensure-status.feature')
    * def orderUntil = read('common/order-until.feature')
    * def newOrderPrefix = function(suffix) { return 'ORD-MX-E2EC' + runId + suffix + '-' }
    * def payload = function(record) { return karate.fromString(record.value) }
    * def quoted = function(version) { return '"' + version + '"' }
    * def clientUrl = clientsUrl + '/clients/CLI-70001'
    * def productUrl = productsUrl + '/products/PRD-020'
    * def productQuery = { market: 'MX' }
    * def productKey = 'MX:PRD-020'

  Scenario: blocking a cached client rejects the next order and unblocking approves again
    * call ensureStatus { resourceUrl: '#(clientUrl)', wantedStatus: 'ACTIVE' }
    * def before = call getEntity { resourceUrl: '#(clientUrl)' }
    And match before.etag == quoted(before.entity.version)
    * def clientOrder = { clientId: 'CLI-70001' }
    * def warm = ({ orderPrefix: newOrderPrefix('W'), fields: clientOrder })
    * call orderUntil karate.merge(warm, { expectedStatus: 'APPROVED' })
    * def changesMark = kafka.mark(topics.clientsChanged)
    * def block = { resourceUrl: '#(clientUrl)', body: { status: 'BLOCKED' } }
    * def blocked = call patchEntity karate.merge(block, { ifMatch: before.etag })
    And match blocked.status == 200
    And match blocked.entity.status == 'BLOCKED'
    And match blocked.entity.version == before.entity.version + 1
    * def events = kafka.readAfter(changesMark, 'CLI-70001', 1, waits.eventMillis)
    * def expectedEvent = { status: 'BLOCKED', version: '#(blocked.entity.version)' }
    And match payload(events[0]) contains expectedEvent
    * def reject = ({ orderPrefix: newOrderPrefix('B'), fields: clientOrder })
    * def rejected = call orderUntil karate.merge(reject, { expectedStatus: 'REJECTED' })
    And match rejected.order.reason == 'CLIENT_NOT_ACTIVE'
    * def unblockMark = kafka.mark(topics.clientsChanged)
    * def unblock = { resourceUrl: '#(clientUrl)', body: { status: 'ACTIVE' } }
    * def active = call patchEntity karate.merge(unblock, { ifMatch: blocked.etag })
    And match active.status == 200
    * def unblockEvents = kafka.readAfter(unblockMark, 'CLI-70001', 1, waits.eventMillis)
    And match payload(unblockEvents[0]) contains { status: 'ACTIVE' }
    * def approve = ({ orderPrefix: newOrderPrefix('U'), fields: clientOrder })
    * call orderUntil karate.merge(approve, { expectedStatus: 'APPROVED' })

  Scenario: discontinuing a cached product rejects the next order and reactivating approves again
    * def productArgs = { resourceUrl: '#(productUrl)', query: '#(productQuery)' }
    * call ensureStatus karate.merge(productArgs, { wantedStatus: 'ACTIVE' })
    * def before = call getEntity productArgs
    * def items = [{ productId: 'PRD-020', quantity: 2, unitPrice: 10.0 }]
    * def productOrder = { items: '#(items)' }
    * def warm = ({ orderPrefix: newOrderPrefix('PW'), fields: productOrder })
    * call orderUntil karate.merge(warm, { expectedStatus: 'APPROVED' })
    * def changesMark = kafka.mark(topics.productsChanged)
    * def discontinue = karate.merge(productArgs, { body: { status: 'DISCONTINUED' } })
    * def discontinued = call patchEntity karate.merge(discontinue, { ifMatch: before.etag })
    And match discontinued.status == 200
    And match discontinued.entity.status == 'DISCONTINUED'
    * def events = kafka.readAfter(changesMark, productKey, 1, waits.eventMillis)
    * def expectedEvent = { status: 'DISCONTINUED', version: '#(discontinued.entity.version)' }
    And match payload(events[0]) contains expectedEvent
    * def reject = ({ orderPrefix: newOrderPrefix('PB'), fields: productOrder })
    * def rejected = call orderUntil karate.merge(reject, { expectedStatus: 'REJECTED' })
    And match rejected.order.reason == 'PRODUCT_NOT_ACTIVE'
    * def reactivateMark = kafka.mark(topics.productsChanged)
    * def reactivate = karate.merge(productArgs, { body: { status: 'ACTIVE' } })
    * def active = call patchEntity karate.merge(reactivate, { ifMatch: discontinued.etag })
    And match active.status == 200
    * def reactivated = kafka.readAfter(reactivateMark, productKey, 1, waits.eventMillis)
    And match payload(reactivated[0]) contains { status: 'ACTIVE' }
    * def approve = ({ orderPrefix: newOrderPrefix('PU'), fields: productOrder })
    * call orderUntil karate.merge(approve, { expectedStatus: 'APPROVED' })

  Scenario: a stale If-Match is rejected with 412 and the version does not change
    * def bump = { resourceUrl: '#(clientUrl)', body: { segment: 'WHOLESALE' } }
    * call patchEntity bump
    * def current = call getEntity { resourceUrl: '#(clientUrl)' }
    * def staleTag = quoted(current.entity.version - 1)
    * def change = { resourceUrl: '#(clientUrl)', body: { segment: 'WHOLESALE' } }
    * def stale = call patchEntity karate.merge(change, { ifMatch: staleTag })
    And match stale.status == 412
    And match stale.entity.code == 'PRECONDITION_FAILED'
    * def after = call getEntity { resourceUrl: '#(clientUrl)' }
    And match after.entity.version == current.entity.version

  Scenario: changing master data requires the admin roles
    * def byAnalyst = { token: '#(tokens.analyst)', body: { status: 'BLOCKED' } }
    * def client = call patchEntity karate.merge(byAnalyst, { resourceUrl: clientUrl })
    And match client.status == 403
    * def productChange = karate.merge(byAnalyst, { resourceUrl: productUrl, query: productQuery })
    * def product = call patchEntity productChange
    And match product.status == 403
