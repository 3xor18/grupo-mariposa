@ignore
Feature: Change a client or a product through its admin API with optimistic concurrency

  Scenario:
    Given url resourceUrl
    And params karate.get('query', {})
    And header Authorization = 'Bearer ' + karate.get('token', tokens.admin)
    And header If-Match = karate.get('ifMatch', null)
    And request body
    When method patch
    * def status = responseStatus
    * def etag = karate.response.header('ETag')
    * def entity = response
