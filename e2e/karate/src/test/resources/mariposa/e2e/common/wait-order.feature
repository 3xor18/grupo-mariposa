@ignore
Feature: Poll the orders query API until the order matches the expected status and version

  Scenario:
    * def attempts = karate.get('retries', 60)
    * def version = karate.get('expectedVersion', null)
    * def versionOk = function(o) { return version == null || o.eventVersion == version }
    * def reached = function(o) { return o.status == expectedStatus && versionOk(o) }
    * configure retry = { count: '#(attempts)', interval: 1000 }
    Given url ordersUrl + '/orders/' + orderId
    And header Authorization = 'Bearer ' + tokens.analyst
    And retry until responseStatus == 200 && reached(response)
    When method get
    * def order = response
