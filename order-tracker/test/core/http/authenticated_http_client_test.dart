import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:mocktail/mocktail.dart';
import 'package:order_tracker/core/http/authenticated_http_client.dart';

import '../../helpers/mocks.dart';

void main() {
  late MockHttpClient inner;
  late MockAccessTokenProvider tokens;
  late AuthenticatedHttpClient client;
  final uri = Uri.parse('https://tracker.example/api/orders');

  setUpAll(registerCommonFallbacks);

  setUp(() {
    inner = MockHttpClient();
    tokens = MockAccessTokenProvider();
    client = AuthenticatedHttpClient(inner, tokens);
  });

  void respondWith(int status) {
    when(
      () => inner.send(any()),
    ).thenAnswer((_) async => http.StreamedResponse(const Stream.empty(), status));
  }

  http.BaseRequest sentRequest() {
    return verify(() => inner.send(captureAny())).captured.single as http.BaseRequest;
  }

  test('should attach the bearer token when a session exists', () async {
    when(tokens.validAccessToken).thenAnswer((_) async => 'token-123');
    respondWith(200);
    await client.get(uri);
    expect(sentRequest().headers['authorization'], 'Bearer token-123');
    verifyNever(tokens.onUnauthorized);
  });

  test('should send the request without authorization when there is no token', () async {
    when(tokens.validAccessToken).thenAnswer((_) async => null);
    respondWith(200);
    await client.get(uri);
    expect(sentRequest().headers.containsKey('authorization'), isFalse);
  });

  test('should notify the token provider when the api answers 401', () async {
    when(tokens.validAccessToken).thenAnswer((_) async => 'expired');
    respondWith(401);
    final response = await client.get(uri);
    expect(response.statusCode, 401);
    verify(tokens.onUnauthorized).called(1);
  });

  test('should close the inner client', () {
    client.close();
    verify(inner.close).called(1);
  });
}
