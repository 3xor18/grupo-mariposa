# Contract — `orders.processing.dlt`

Owner: **order-processor team**. Key: `orderId` when it could be extracted, otherwise the original key.

## Value
The **original message bytes**, untouched. The DLT never re-serializes the payload, so an unreadable
message can still be inspected and replayed as-is.

## Headers

| Header | Type | Always | Description |
|---|---|---|---|
| `x-error-category` | string | yes | `DESERIALIZATION`, `VALIDATION`, `VERSION_CONFLICT`, `EXTERNAL_TRANSIENT`, `EXTERNAL_PERMANENT`, `PERSISTENCE`, `UNEXPECTED` |
| `x-error-cause` | string | yes | Short cause, max 256 chars, never includes PII, tokens or payload values |
| `x-attempts` | int as string | yes | Record-level attempts performed before giving up |
| `x-failed-at` | ISO-8601 UTC | yes | When the message was sent to the DLT |
| `x-component` | string | yes | `order-processor` |
| `x-order-id` | string | when available | Extracted from the payload |
| `x-event-id` | string | when available | Extracted from the payload |
| `kafka_dlt-original-topic` | string | yes | Added by Spring Kafka |
| `kafka_dlt-original-partition` | int | yes | Added by Spring Kafka |
| `kafka_dlt-original-offset` | long | yes | Added by Spring Kafka |
| `traceparent` | W3C | when available | Trace that failed |

## Replay
Messages in `EXTERNAL_TRANSIENT`, `PERSISTENCE` and `UNEXPECTED` can be replayed to `orders.created.v1`
without changes once the cause is fixed: the inbox dedup and the `TECHNICAL_FAILURE` retry rule make replay safe.
`VALIDATION`, `DESERIALIZATION` and `VERSION_CONFLICT` require the producer to publish a corrected event.
