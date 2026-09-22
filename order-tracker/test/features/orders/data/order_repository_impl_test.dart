import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:mocktail/mocktail.dart';
import 'package:order_tracker/core/error/app_failure.dart';
import 'package:order_tracker/core/http/api_client.dart';
import 'package:order_tracker/core/result/result.dart';
import 'package:order_tracker/features/orders/data/datasources/orders_api.dart';
import 'package:order_tracker/features/orders/data/repositories/order_repository_impl.dart';
import 'package:order_tracker/features/orders/domain/entities/market.dart';
import 'package:order_tracker/features/orders/domain/entities/order.dart';
import 'package:order_tracker/features/orders/domain/entities/order_page.dart';
import 'package:order_tracker/features/orders/domain/entities/order_status.dart';
import 'package:order_tracker/features/orders/domain/entities/orders_filter.dart';

import '../../../fixtures/order_fixtures.dart';
import '../../../helpers/mocks.dart';

void main() {
  late MockHttpClient httpClient;
  late OrderRepositoryImpl repository;

  setUpAll(registerCommonFallbacks);

  setUp(() {
    httpClient = MockHttpClient();
    final apiClient = ApiClient(httpClient, Uri.parse('http://localhost:8090/api'));
    repository = OrderRepositoryImpl(OrdersApi(apiClient));
  });

  void respond(Object body, int status, {String type = 'application/json'}) {
    when(() => httpClient.get(any(), headers: any(named: 'headers'))).thenAnswer(
      (_) async => http.Response.bytes(
        utf8.encode(jsonEncode(body)),
        status,
        headers: {'content-type': type},
      ),
    );
  }

  Uri requestedUri() {
    return verify(
          () => httpClient.get(captureAny(), headers: any(named: 'headers')),
        ).captured.single
        as Uri;
  }

  group('findById', () {
    test('should request the order and map it to the domain', () async {
      respond(approvedOrderJson(), 200);
      final result = await repository.findById(approvedOrderId);
      expect(result, Ok<Order>(approvedOrder()));
      expect(requestedUri().path, '/api/orders/ORD-MX-000147');
    });

    test('should surface ORDER_NOT_FOUND as a not found failure', () async {
      respond(
        {
          'type': 'about:blank',
          'title': 'Not Found',
          'status': 404,
          'code': 'ORDER_NOT_FOUND',
          'detail': 'Order ORD-X was not found',
          'instance': '/orders/ORD-X',
          'traceId': 'trace-404',
          'timestamp': '2026-09-18T15:42:12Z',
        },
        404,
        type: 'application/problem+json',
      );
      expect(
        await repository.findById('ORD-X'),
        const Err<Order>(NotFoundFailure(traceId: 'trace-404')),
      );
    });

    test('should surface contract violations as unexpected responses', () async {
      respond(approvedOrderJson()..remove('orderId'), 200);
      expect(
        await repository.findById(approvedOrderId),
        const Err<Order>(UnexpectedResponseFailure()),
      );
    });

    test('should surface invalid amounts as unexpected responses', () async {
      final json = approvedOrderJson();
      (json['totals']! as Map<String, Object?>)['tax'] = 1.005;
      respond(json, 200);
      expect(
        await repository.findById(approvedOrderId),
        const Err<Order>(UnexpectedResponseFailure()),
      );
    });

    test('should surface bodies that are not objects as unexpected responses', () async {
      respond(['not', 'an', 'object'], 200);
      expect(
        await repository.findById(approvedOrderId),
        const Err<Order>(UnexpectedResponseFailure()),
      );
    });
  });

  group('list', () {
    test('should send filters and paging as contract query parameters', () async {
      respond(orderPageJson(), 200);
      final result = await repository.list(
        filter: const OrdersFilter(status: OrderStatus.rejected, market: Market.co),
        page: 1,
        size: 20,
      );
      expect(result, isA<Ok<OrderPage>>());
      expect(requestedUri().queryParameters, {
        'status': 'REJECTED',
        'market': 'CO',
        'page': '1',
        'size': '20',
      });
    });

    test('should omit empty filters', () async {
      respond(orderPageJson(), 200);
      await repository.list(filter: const OrdersFilter(), page: 0, size: 20);
      expect(requestedUri().queryParameters, {'page': '0', 'size': '20'});
    });

    test('should propagate server failures', () async {
      respond({'status': 503, 'code': 'SERVICE_UNAVAILABLE'}, 503);
      expect(
        await repository.list(filter: const OrdersFilter(), page: 0, size: 20),
        const Err<OrderPage>(ServerFailure(statusCode: 503)),
      );
    });
  });
}
