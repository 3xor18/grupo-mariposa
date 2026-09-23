@ignore
Feature: Poll the orders query API until the order reaches any final status

  Scenario:
    * def attempts = karate.get('retries', 60)
    * def finalStatuses = ['APPROVED', 'REJECTED', 'TECHNICAL_FAILURE']
    * def isFinal = function(o) { return finalStatuses.indexOf(o.status) >= 0 }
    * configure retry = { count: '#(attempts)', interval: 1000 }
    Given url ordersUrl + '/orders/' + orderId
    And header Authorization = 'Bearer ' + tokens.analyst
    And retry until responseStatus == 200 && isFinal(response)
    When method get
    * def order = response
