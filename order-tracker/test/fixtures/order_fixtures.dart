import 'package:order_tracker/features/orders/domain/entities/order.dart';
import 'package:order_tracker/features/orders/domain/entities/order_line.dart';
import 'package:order_tracker/features/orders/domain/entities/order_status.dart';
import 'package:order_tracker/features/orders/domain/entities/order_summary.dart';

const approvedOrderId = 'ORD-MX-000147';
const rejectedOrderId = 'ORD-CO-000201';
const failedOrderId = 'ORD-MX-000999';

Map<String, Object?> approvedOrderJson() => {
  'orderId': approvedOrderId,
  'sourceEventId': '01J8ZP6M5E4RH0K7Y2N9A3TQWX',
  'eventVersion': 1,
  'status': 'APPROVED',
  'market': 'MX',
  'currency': 'MXN',
  'channel': 'C1',
  'client': {
    'clientId': 'CLI-99821',
    'name': 'Distribuidora Central',
    'status': 'ACTIVE',
    'segment': 'WHOLESALE',
    'taxRegime': 'GENERAL',
    'market': 'MX',
  },
  'lines': [
    {
      'productId': 'PRD-001',
      'name': 'Bebida 600 ml',
      'sku': 'BEB-600-PET',
      'taxCategory': 'STANDARD',
      'quantity': 24,
      'unitPrice': 35.5,
      'grossSubtotal': 852.0,
      'discountRate': 0.03,
      'discount': 25.56,
      'netSubtotal': 826.44,
      'taxRate': 0.16,
      'taxAmount': 132.23,
      'lineTotal': 958.67,
    },
    {'productId': 'PRD-008', 'quantity': 12, 'unitPrice': 82},
  ],
  'totals': {
    'grossSubtotal': 1836.0,
    'discount': 55.08,
    'netSubtotal': 1780.92,
    'tax': 319.19,
    'grandTotal': 2100.11,
  },
  'reason': null,
  'violations': <Object?>[],
  'failure': null,
  'occurredAt': '2026-09-18T15:42:10Z',
  'receivedAt': '2026-09-18T15:42:11Z',
  'processedAt': '2026-09-18T15:42:12Z',
  'traceId': '4bf92f3577b34da6a3ce929d0e0e4736',
};

Map<String, Object?> minimalOrderJson() => {
  'orderId': rejectedOrderId,
  'sourceEventId': 'evt-2',
  'eventVersion': 1,
  'status': 'REJECTED',
  'market': 'CO',
  'currency': 'COP',
  'client': {'clientId': 'CLI-20002'},
  'lines': [
    {'productId': 'PRD-007', 'quantity': 3, 'unitPrice': 4500},
  ],
  'totals': {
    'grossSubtotal': 13500,
    'discount': 0,
    'netSubtotal': 13500,
    'tax': 0,
    'grandTotal': 0,
  },
  'reason': 'CLIENT_BLOCKED',
  'violations': [
    {'code': 'CLIENT_BLOCKED', 'message': 'El cliente está bloqueado'},
    {'code': 'PRODUCT_DISCONTINUED', 'message': 'Producto descontinuado', 'productId': 'PRD-007'},
  ],
  'receivedAt': '2026-09-18T16:00:00Z',
  'processedAt': '2026-09-18T16:00:01Z',
};

Map<String, Object?> orderPageJson({int page = 0, int totalPages = 2}) => {
  'items': [
    {
      'orderId': approvedOrderId,
      'status': 'APPROVED',
      'market': 'MX',
      'currency': 'MXN',
      'clientId': 'CLI-99821',
      'eventVersion': 1,
      'grandTotal': 2100.11,
      'reason': null,
      'processedAt': '2026-09-18T15:42:12Z',
    },
  ],
  'page': page,
  'size': 20,
  'totalElements': 21,
  'totalPages': totalPages,
};

final processedAt = DateTime.utc(2026, 9, 18, 15, 42, 12);

const approvedTotals = OrderTotals(
  grossSubtotal: 1836,
  discount: 55.08,
  netSubtotal: 1780.92,
  tax: 319.19,
  grandTotal: 2100.11,
);

const detailedLine = OrderLine(
  productId: 'PRD-001',
  name: 'Bebida 600 ml',
  sku: 'BEB-600-PET',
  taxCategory: 'STANDARD',
  quantity: 24,
  unitPrice: 35.5,
  grossSubtotal: 852,
  discountRate: 0.03,
  discount: 25.56,
  netSubtotal: 826.44,
  taxRate: 0.16,
  taxAmount: 132.23,
  lineTotal: 958.67,
);

const bareLine = OrderLine(productId: 'PRD-008', quantity: 12, unitPrice: 82);

Order approvedOrder() => Order(
  orderId: approvedOrderId,
  status: OrderStatus.approved,
  market: 'MX',
  currency: 'MXN',
  channel: 'C1',
  client: const OrderClient(
    clientId: 'CLI-99821',
    name: 'Distribuidora Central',
    status: 'ACTIVE',
    segment: 'WHOLESALE',
    taxRegime: 'GENERAL',
    market: 'MX',
  ),
  lines: const [detailedLine, bareLine],
  totals: approvedTotals,
  occurredAt: DateTime.utc(2026, 9, 18, 15, 42, 10),
  receivedAt: DateTime.utc(2026, 9, 18, 15, 42, 11),
  processedAt: processedAt,
  traceId: '4bf92f3577b34da6a3ce929d0e0e4736',
);

Order rejectedOrder() => Order(
  orderId: rejectedOrderId,
  status: OrderStatus.rejected,
  market: 'CO',
  currency: 'COP',
  client: const OrderClient(clientId: 'CLI-20002'),
  lines: const [OrderLine(productId: 'PRD-007', quantity: 3, unitPrice: 4500)],
  totals: const OrderTotals(
    grossSubtotal: 13500,
    discount: 0,
    netSubtotal: 13500,
    tax: 0,
    grandTotal: 0,
  ),
  reason: 'CLIENT_BLOCKED',
  violations: const [
    Violation(code: 'CLIENT_BLOCKED', message: 'El cliente está bloqueado'),
    Violation(
      code: 'PRODUCT_DISCONTINUED',
      message: 'Producto descontinuado',
      productId: 'PRD-007',
    ),
  ],
  receivedAt: DateTime.utc(2026, 9, 18, 16),
  processedAt: DateTime.utc(2026, 9, 18, 16, 0, 1),
);

Order failedOrder() => Order(
  orderId: failedOrderId,
  status: OrderStatus.technicalFailure,
  market: 'PE',
  currency: 'PEN',
  client: const OrderClient(clientId: 'CLI-40002'),
  lines: const [],
  totals: const OrderTotals(grossSubtotal: 0, discount: 0, netSubtotal: 0, tax: 0, grandTotal: 0),
  failure: const ProcessingFailure(
    category: 'DEPENDENCY_UNAVAILABLE',
    cause: 'clients-api 503',
    attempts: 5,
  ),
  receivedAt: DateTime.utc(2026, 9, 18, 17),
  processedAt: DateTime.utc(2026, 9, 18, 17, 0, 30),
);

OrderSummary summary(String orderId, {OrderStatus status = OrderStatus.approved}) => OrderSummary(
  orderId: orderId,
  status: status,
  market: 'MX',
  currency: 'MXN',
  clientId: 'CLI-99821',
  grandTotal: 2100.11,
  processedAt: processedAt,
);

OrderPage orderPage({
  required List<OrderSummary> items,
  int page = 0,
  int totalPages = 1,
  int? totalElements,
}) => OrderPage(
  items: items,
  page: page,
  size: 20,
  totalElements: totalElements ?? items.length,
  totalPages: totalPages,
);
