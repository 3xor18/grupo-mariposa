import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:mocktail/mocktail.dart';
import 'package:order_tracker/core/error/app_failure.dart';
import 'package:order_tracker/core/http/api_client.dart';
import 'package:order_tracker/core/result/result.dart';

import '../../helpers/mocks.dart';

void main() {
  late MockHttpClient httpClient;
  late ApiClient apiClient;
  final baseUri = Uri.parse('https://tracker.example/api');

  setUpAll(registerCommonFallbacks);

  setUp(() {
    httpClient = MockHttpClient();
    apiClient = ApiClient(httpClient, baseUri);
  });

  void respond(http.Response response) {
    when(
      () => httpClient.get(any(), headers: any(named: 'headers')),
    ).thenAnswer((_) async => response);
  }

  http.Response jsonResponse(Object body, int status, {String type = 'application/json'}) {
    return http.Response.bytes(
      utf8.encode(jsonEncode(body)),
      status,
      headers: {'content-type': type},
    );
  }

  test('should resolve path segments and query against the base uri', () {
    expect(
      apiClient.resolve(['orders', 'ORD 1/2']).toString(),
      'https://tracker.example/api/orders/ORD%201%2F2',
    );
    expect(
      apiClient.resolve(['orders'], query: {'page': '0'}).toString(),
      'https://tracker.example/api/orders?page=0',
    );
  });

  test('should decode a successful json body', () async {
    respond(jsonResponse({'orderId': 'Añejo'}, 200));
    final result = await apiClient.getJson(['orders']);
    expect(result, const Ok<Object?>({'orderId': 'Añejo'}));
    final captured = verify(
      () => httpClient.get(captureAny(), headers: captureAny(named: 'headers')),
    ).captured;
    expect(captured.first, Uri.parse('https://tracker.example/api/orders'));
    expect((captured.last as Map<String, String>)['accept'], contains('problem+json'));
  });

  test('should fail with unexpected response when a success body is not json', () async {
    respond(http.Response('<html>', 200));
    expect(
      await apiClient.getJson(['orders']),
      const Err<Object?>(UnexpectedResponseFailure()),
    );
  });

  test('should map problem+json errors into typed failures', () async {
    respond(
      jsonResponse(
        {'status': 404, 'code': 'ORDER_NOT_FOUND', 'traceId': 'abc'},
        404,
        type: 'application/problem+json',
      ),
    );
    expect(
      await apiClient.getJson(['orders', 'X']),
      const Err<Object?>(NotFoundFailure(traceId: 'abc')),
    );
  });

  test('should ignore error bodies that are not problem documents', () async {
    respond(http.Response('Bad gateway', 502, headers: {'content-type': 'text/html'}));
    expect(
      await apiClient.getJson(['orders']),
      const Err<Object?>(ServerFailure(statusCode: 502)),
    );
    respond(http.Response('{broken', 500, headers: {'content-type': 'application/json'}));
    expect(
      await apiClient.getJson(['orders']),
      const Err<Object?>(ServerFailure(statusCode: 500)),
    );
    respond(http.Response('', 503));
    expect(
      await apiClient.getJson(['orders']),
      const Err<Object?>(ServerFailure(statusCode: 503)),
    );
  });

  test('should pass the retry after header to rate limited failures', () async {
    respond(http.Response('', 429, headers: {'retry-after': '2'}));
    expect(
      await apiClient.getJson(['orders']),
      const Err<Object?>(RateLimitedFailure(retryAfter: Duration(seconds: 2))),
    );
  });

  test('should map transport errors to network failures', () async {
    when(
      () => httpClient.get(any(), headers: any(named: 'headers')),
    ).thenThrow(http.ClientException('offline'));
    expect(await apiClient.getJson(['orders']), const Err<Object?>(NetworkFailure()));
  });

  test('should map timeouts to network failures', () async {
    final slowClient = ApiClient(httpClient, baseUri, timeout: Duration.zero);
    when(
      () => httpClient.get(any(), headers: any(named: 'headers')),
    ).thenAnswer((_) => Completer<http.Response>().future);
    expect(await slowClient.getJson(['orders']), const Err<Object?>(NetworkFailure()));
  });
}
