@ignore
Feature: Publish fresh orders until one ends in the expected status (eventually consistent cache)

  Scenario:
    * def maxAttempts = karate.get('attempts', 5)
    * def backoffMillis = karate.get('backoffMillis', 400)
    * def orderEvent = read('classpath:mariposa/e2e/common/order-event.js')
    * def waitFinal = 'classpath:mariposa/e2e/common/wait-final.feature'
    * def publish = 'classpath:mariposa/e2e/common/publish-order.feature'
    * def placeOrder =
      """
      function(attempt) {
        var orderId = orderPrefix + attempt;
        var event = orderEvent(karate.merge(fields, { orderId: orderId }));
        karate.call(publish, { event: event });
        return karate.call(waitFinal, { orderId: orderId }).order;
      }
      """
    * def settle =
      """
      function() {
        var last = null;
        for (var attempt = 1; attempt <= maxAttempts; attempt++) {
          last = placeOrder(attempt);
          if (last.status == expectedStatus) {
            return last;
          }
          java.lang.Thread.sleep(backoffMillis * attempt);
        }
        return last;
      }
      """
    * def order = settle()
    * match order.status == expectedStatus
