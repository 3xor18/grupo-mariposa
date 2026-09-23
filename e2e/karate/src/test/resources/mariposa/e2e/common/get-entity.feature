@ignore
Feature: Read a client or a product as admin and keep its ETag

  Scenario:
    Given url resourceUrl
    And params karate.get('query', {})
    And header Authorization = 'Bearer ' + tokens.admin
    When method get
    Then status 200
    * def etag = karate.response.header('ETag')
    * def entity = response
