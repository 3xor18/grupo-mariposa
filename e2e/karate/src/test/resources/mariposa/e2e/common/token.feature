@ignore
Feature: Obtain an access token from Keycloak for a demo user

  Scenario:
    Given url keycloakUrl + '/realms/mariposa/protocol/openid-connect/token'
    And form field grant_type = 'password'
    And form field client_id = 'orders-cli'
    And form field username = username
    And form field password = demoPassword
    When method post
    Then status 200
    * def accessToken = response.access_token
