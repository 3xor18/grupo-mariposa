@ignore
Feature: Poll the orders query API until the order reaches the expected status

  Scenario:
    * configure retry = { count: '#(karate.get("retries", 60))', interval: 1000 }
    Given url ordersUrl + '/orders/' + orderId
    And header Authorization = 'Bearer ' + token
    And retry until responseStatus == 200 && response.status == expectedStatus
    When method get
    * def order = response
