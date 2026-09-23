Feature: effective-dated tax rates with four-eyes approval (ADR 0008)

  Background:
    * url ordersUrl
    * def problem = read('common/problem.json')
    * def orderEvent = read('common/order-event.js')
    * def publishOrder = read('common/publish-order.feature')
    * def waitOrder = read('common/wait-order.feature')
    * def Instant = Java.type('java.time.Instant')
    * def Duration = Java.type('java.time.Duration')
    * def ChronoUnit = Java.type('java.time.temporal.ChronoUnit')
    * def market = 'EC'
    * def category = 'REDUCED'
    * def lead = Duration.ofDays(taxRateLeadDays)
    * def start = Instant.now().plus(lead).truncatedTo(ChronoUnit.SECONDS)
    * def validFrom = start + ''
    * def shifted = function(seconds) { return start.plusSeconds(seconds) + '' }
    * def sameInstant = function(a, b) { return Instant.parse(a).equals(Instant.parse(b)) }
    * def isOpenEnded = function(rate) { return rate.validTo == null }
    * def taxOf = function(net, rate) { return Math.round(net * rate * 100) / 100 }
    * def newOrderId = function(id) { return 'ORD-EC-E2ET' + runId + id }
    * def items = [{ productId: 'PRD-019', quantity: 5, unitPrice: 8.4 }]
    * def netSubtotal = 42.00
    * def orderFields = { market: 'EC', currency: 'USD', clientId: 'CLI-60001', items: '#(items)' }
    * def proposal =
      """
      {
        market: '#(market)',
        category: '#(category)',
        validFrom: '#(validFrom)',
        changeReason: 'Karate four-eyes scenario'
      }
      """

  Scenario: only orders-admin can read or change tax rates
    Given path 'tax-rates'
    When method get
    Then status 401
    Given path 'tax-rates'
    And header Authorization = 'Bearer ' + tokens.analyst
    When method get
    Then status 403
    And match response == problem
    Given path 'tax-rates'
    And header Authorization = 'Bearer ' + tokens.analyst
    And request karate.merge(proposal, { rate: 0.12 })
    When method post
    Then status 403

  Scenario: the proposer cannot approve, a second admin can, and the rate applies by occurredAt
    Given path 'tax-rates'
    And param market = market
    And param category = category
    And param status = 'APPROVED'
    And header Authorization = 'Bearer ' + tokens.admin
    When method get
    Then status 200
    * def current = karate.filter(response, isOpenEnded)
    And match current == '#[1]'
    * def oldRate = current[0].rate
    * def newRate = oldRate == 0.12 ? 0.13 : 0.12
    Given path 'tax-rates'
    And header Authorization = 'Bearer ' + tokens.admin
    And request karate.merge(proposal, { rate: newRate })
    When method post
    Then status 201
    And match response contains { status: 'PROPOSED', proposedBy: 'admin', rate: '#(newRate)' }
    * def id = response.id
    Given path 'tax-rates', id, 'approve'
    And header Authorization = 'Bearer ' + tokens.admin
    And request {}
    When method post
    Then status 403
    And match response == problem
    And match response.code == 'FOUR_EYES_REQUIRED'
    Given path 'tax-rates', id, 'approve'
    And header Authorization = 'Bearer ' + tokens.auditor
    And request {}
    When method post
    Then status 200
    And match response contains { status: 'APPROVED', reviewedBy: 'auditor', validTo: null }
    Given path 'tax-rates'
    And param market = market
    And param category = category
    And param status = 'APPROVED'
    And header Authorization = 'Bearer ' + tokens.admin
    When method get
    Then status 200
    * def closed = karate.filter(response, function(r) { return r.id == current[0].id })[0]
    * assert sameInstant(closed.validTo, validFrom)
    * def afterId = newOrderId('A')
    * def afterFields = { orderId: '#(afterId)', occurredAt: '#(shifted(3600))' }
    * def afterEvent = orderEvent(karate.merge(orderFields, afterFields))
    * call publishOrder { event: '#(afterEvent)' }
    * def after = call waitOrder { orderId: '#(afterId)', expectedStatus: 'APPROVED' }
    And match after.order.lines[0].taxRate == newRate
    And match after.order.totals.tax == taxOf(netSubtotal, newRate)
    * assert sameInstant(after.order.taxRateEffectiveFrom, validFrom)
    * def beforeId = newOrderId('B')
    * def beforeFields = { orderId: '#(beforeId)', occurredAt: '#(shifted(-1))' }
    * def beforeEvent = orderEvent(karate.merge(orderFields, beforeFields))
    * call publishOrder { event: '#(beforeEvent)' }
    * def before = call waitOrder { orderId: '#(beforeId)', expectedStatus: 'APPROVED' }
    And match before.order.lines[0].taxRate == oldRate
    And match before.order.totals.tax == taxOf(netSubtotal, oldRate)
    * assert Instant.parse(before.order.taxRateEffectiveFrom).isBefore(start)

  Scenario: a rejected proposal cannot be approved and invalid proposals are 400
    Given path 'tax-rates'
    And header Authorization = 'Bearer ' + tokens.admin
    And request karate.merge(proposal, { rate: 0.12, validFrom: '2020-01-01T00:00:00Z' })
    When method post
    Then status 400
    And match response == problem
    And match response.errors[*].field contains 'validFrom'
    Given path 'tax-rates'
    And header Authorization = 'Bearer ' + tokens.admin
    And request karate.merge(proposal, { rate: 0.12 })
    When method post
    Then status 201
    * def id = response.id
    Given path 'tax-rates', id, 'reject'
    And header Authorization = 'Bearer ' + tokens.auditor
    And request {}
    When method post
    Then status 200
    And match response.status == 'REJECTED'
    Given path 'tax-rates', id, 'approve'
    And header Authorization = 'Bearer ' + tokens.auditor
    And request {}
    When method post
    Then status 409
    And match response.code == 'TAX_RATE_CONFLICT'
    Given path 'tax-rates', 'TR-DOES-NOT-EXIST', 'approve'
    And header Authorization = 'Bearer ' + tokens.auditor
    And request {}
    When method post
    Then status 404
    And match response.code == 'TAX_RATE_NOT_FOUND'
