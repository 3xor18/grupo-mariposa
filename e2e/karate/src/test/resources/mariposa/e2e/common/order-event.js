function fn(args) {
  return {
    eventId: args.eventId || java.util.UUID.randomUUID() + '',
    eventVersion: args.eventVersion || 1,
    occurredAt: args.occurredAt || '2026-09-18T15:42:10Z',
    orderId: args.orderId,
    market: args.market || 'MX',
    currency: args.currency || 'MXN',
    clientId: args.clientId || 'CLI-99821',
    channel: 'C1',
    items: args.items || [
      { productId: 'PRD-001', quantity: 24, unitPrice: 35.5 },
      { productId: 'PRD-008', quantity: 12, unitPrice: 82.0 }
    ]
  };
}
