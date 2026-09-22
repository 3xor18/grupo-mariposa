# Platform E2E (Karate)

Suite de aceptación que corre contra la plataforma levantada con `./mariposa.sh up`.
Publica eventos reales en Kafka, consulta la API de pedidos y lee `orders.processed.v1` y
`orders.processing.dlt` para verificar efectos, idempotencia, conflictos, versiones y resiliencia.

```bash
./mariposa.sh e2e
# o solo Karate
cd e2e/karate && ./mvnw test -Ddemo.password=<DEMO_USER_PASSWORD>
```

Reporte HTML: `target/karate-reports/karate-summary.html`.
