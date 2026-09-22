function fn() {
  var prop = function (name, fallback) {
    var envName = name.toUpperCase().replace(/\./g, '_');
    return karate.properties[name] || java.lang.System.getenv(envName) || fallback;
  };
  var config = {
    ordersUrl: prop('orders.url', 'http://localhost:8080'),
    productsUrl: prop('products.url', 'http://localhost:8081'),
    clientsUrl: prop('clients.url', 'http://localhost:8082'),
    keycloakUrl: prop('keycloak.url', 'http://localhost:8180'),
    kafkaBootstrap: prop('kafka.bootstrap', 'localhost:19092'),
    demoPassword: prop('demo.password', ''),
    topics: {
      created: 'orders.created.v1',
      processed: 'orders.processed.v1',
      dlt: 'orders.processing.dlt'
    },
    waits: {
      eventMillis: 30000,
      quietMillis: 3000
    },
    runId: java.lang.System.currentTimeMillis() + ''
  };
  var Gateway = Java.type('mariposa.e2e.KafkaGateway');
  config.kafka = new Gateway(config.kafkaBootstrap);
  config.tokens = karate.callSingle('classpath:mariposa/e2e/common/tokens.feature', config).tokens;
  karate.configure('connectTimeout', 5000);
  karate.configure('readTimeout', 15000);
  return config;
}
