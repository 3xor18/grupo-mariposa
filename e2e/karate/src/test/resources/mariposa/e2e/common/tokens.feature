@ignore
Feature: Obtain one access token per demo user, shared by the whole run through callSingle

  Scenario:
    * def token = read('classpath:mariposa/e2e/common/token.feature')
    * def admin = call token { username: 'admin' }
    * def analyst = call token { username: 'analyst' }
    * def viewer = call token { username: 'viewer' }
    * def auditor = call token { username: 'auditor' }
    * def tokens = {}
    * set tokens.admin = admin.accessToken
    * set tokens.analyst = analyst.accessToken
    * set tokens.viewer = viewer.accessToken
    * set tokens.auditor = auditor.accessToken
