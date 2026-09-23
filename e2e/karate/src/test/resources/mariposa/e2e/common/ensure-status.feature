@ignore
Feature: Put a demo client or product back to the given status so the suite can be rerun

  Scenario:
    * def query = karate.get('query', {})
    * def target = { resourceUrl: '#(resourceUrl)', query: '#(query)' }
    * def current = call read('classpath:mariposa/e2e/common/get-entity.feature') target
    * def reset = karate.merge(target, { body: { status: wantedStatus }, ifMatch: current.etag })
    * def patch = 'classpath:mariposa/e2e/common/patch-entity.feature'
    * if (current.entity.status != wantedStatus) karate.call(patch, reset)
