import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:mocktail/mocktail.dart';
import 'package:order_tracker/core/config/app_config.dart';
import 'package:order_tracker/core/config/config_loader.dart';
import 'package:order_tracker/core/json/json_map.dart';

import '../../helpers/mocks.dart';

void main() {
  const config = AppConfig(
    apiBaseUrl: '/api',
    keycloakUrl: 'http://localhost:8180/',
    realm: 'mariposa',
    clientId: 'order-tracker',
  );
  final appUri = Uri.parse('http://localhost:8090/?code=abc');

  setUpAll(registerCommonFallbacks);

  group('AppConfig', () {
    test('should parse the runtime configuration', () {
      final parsed = AppConfig.fromJson(
        const JsonMap({
          'apiBaseUrl': '/api',
          'keycloakUrl': 'http://localhost:8180/',
          'realm': 'mariposa',
          'clientId': 'order-tracker',
        }),
      );
      expect(parsed, config);
    });

    test('should derive the issuer and api base uris', () {
      expect(config.issuerUri.toString(), 'http://localhost:8180/realms/mariposa');
      expect(config.apiBaseUri(appUri: appUri).toString(), 'http://localhost:8090/api');
    });
  });

  group('ConfigLoader', () {
    late MockHttpClient httpClient;
    late ConfigLoader loader;

    setUp(() {
      httpClient = MockHttpClient();
      loader = ConfigLoader(httpClient);
    });

    void respond(http.Response response) {
      when(() => httpClient.get(any())).thenAnswer((_) async => response);
    }

    test('should load config.json next to the app with a cache buster', () async {
      respond(
        http.Response(
          jsonEncode({
            'apiBaseUrl': '/api',
            'keycloakUrl': 'http://localhost:8180/',
            'realm': 'mariposa',
            'clientId': 'order-tracker',
          }),
          200,
        ),
      );
      expect(await loader.load(appUri: appUri, cacheBuster: '42'), config);
      final uri = verify(() => httpClient.get(captureAny())).captured.single as Uri;
      expect(uri.toString(), 'http://localhost:8090/config.json?v=42');
    });

    test('should fail when the file is missing', () async {
      respond(http.Response('', 404));
      await expectLater(
        loader.load(appUri: appUri, cacheBuster: '1'),
        throwsA(isA<ConfigLoadException>()),
      );
    });

    test('should fail when the file is malformed', () async {
      respond(http.Response('{"realm": 1}', 200));
      await expectLater(
        loader.load(appUri: appUri, cacheBuster: '1'),
        throwsA(isA<ConfigLoadException>()),
      );
    });

    test('should fail when the network is unavailable', () async {
      when(() => httpClient.get(any())).thenThrow(http.ClientException('offline'));
      await expectLater(
        loader.load(appUri: appUri, cacheBuster: '1'),
        throwsA(
          isA<ConfigLoadException>().having(
            (error) => error.uri.path,
            'path',
            '/config.json',
          ),
        ),
      );
    });
  });
}
