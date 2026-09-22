@ignore
Feature: Poll the orders query API until the order matches the expected status and version

  Scenario:
    * def attempts = karate.get('retries', 60)
    * def version = karate.get('expectedVersion', null)
    * configure retry = { count: '#(attempts)', interval: 1000 }
    Given url ordersUrl + '/orders/' + orderId
    And header Authorization = 'Bearer ' + tokens.analyst
    And retry until responseStatus == 200 && response.status == expectedStatus && (version == null || response.eventVersion == version)
    When method get
    * def order = response
