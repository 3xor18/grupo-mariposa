@ignore
Feature: Obtain one access token per demo user, shared by the whole run through callSingle

  Scenario:
    * def admin = call read('classpath:mariposa/e2e/common/token.feature') { username: 'admin' }
    * def analyst = call read('classpath:mariposa/e2e/common/token.feature') { username: 'analyst' }
    * def viewer = call read('classpath:mariposa/e2e/common/token.feature') { username: 'viewer' }
    * def tokens = { admin: '#(admin.accessToken)', analyst: '#(analyst.accessToken)', viewer: '#(viewer.accessToken)' }
