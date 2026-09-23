import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:mocktail/mocktail.dart';
import 'package:order_tracker/core/config/app_config.dart';
import 'package:order_tracker/core/config/config_load_exception.dart';
import 'package:order_tracker/core/config/config_loader.dart';
import 'package:order_tracker/core/json/json_map.dart';

import '../../fixtures/market_fixtures.dart';
import '../../helpers/mocks.dart';

void main() {
  const config = AppConfig(
    apiBaseUrl: '/api',
    keycloakUrl: 'http://localhost:8180/',
    realm: 'mariposa',
    clientId: 'order-tracker',
    redirectUri: 'http://localhost:8090/',
    catalog: testCatalog,
    enableSemantics: true,
  );
  final appUri = Uri.parse('http://localhost:8090/?code=abc');

  setUpAll(registerCommonFallbacks);

  group('AppConfig', () {
    test('should parse the runtime configuration', () {
      final parsed = AppConfig.fromJson(
        JsonMap({
          'apiBaseUrl': '/api',
          'keycloakUrl': 'http://localhost:8180/',
          'realm': 'mariposa',
          'clientId': 'order-tracker',
          'redirectUri': 'http://localhost:8090/',
          ...testCatalogJson(),
          'enableSemantics': true,
        }),
      );
      expect(parsed, config);
    });

    test('should keep semantics disabled unless configured', () {
      final parsed = AppConfig.fromJson(
        JsonMap({
          'apiBaseUrl': '/api',
          'keycloakUrl': 'http://localhost:8180',
          'realm': 'mariposa',
          'clientId': 'order-tracker',
          'redirectUri': 'http://localhost:8090/',
          ...testCatalogJson(),
        }),
      );
      expect(parsed.enableSemantics, isFalse);
      expect(parsed.redirect, Uri.parse('http://localhost:8090/'));
    });

    test('should derive the issuer and api base uris', () {
      expect(config.issuerUri.toString(), 'http://localhost:8180/realms/mariposa');
      expect(config.apiBaseUri(appUri: appUri).toString(), 'http://localhost:8090/api');
    });
  });

  test('should describe load failures', () {
    final error = const ConfigLoadException(
      ConfigLoadFailure.invalidMarketCatalog,
      detail: 'Duplicated market MX',
    ).at(Uri.parse('http://localhost:8090/config.json'));
    expect(
      '$error',
      'ConfigLoadException(invalidMarketCatalog, http://localhost:8090/config.json, '
          'Duplicated market MX)',
    );
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
            'redirectUri': 'http://localhost:8090/',
            ...testCatalogJson(),
            'enableSemantics': true,
          }),
          200,
          headers: {'content-type': 'application/json; charset=utf-8'},
        ),
      );
      expect(await loader.load(appUri: appUri, cacheBuster: '42'), config);
      final uri = verify(() => httpClient.get(captureAny())).captured.single as Uri;
      expect(uri.toString(), 'http://localhost:8090/config.json?v=42');
    });

    Matcher failsWith(ConfigLoadFailure failure) {
      return throwsA(
        isA<ConfigLoadException>().having((error) => error.failure, 'failure', failure),
      );
    }

    test('should fail when the file is missing', () async {
      respond(http.Response('', 404));
      await expectLater(
        loader.load(appUri: appUri, cacheBuster: '1'),
        failsWith(ConfigLoadFailure.unavailable),
      );
    });

    test('should fail when the file is malformed', () async {
      respond(http.Response('{"realm": 1}', 200));
      await expectLater(
        loader.load(appUri: appUri, cacheBuster: '1'),
        failsWith(ConfigLoadFailure.malformed),
      );
    });

    test('should reject a market catalog that breaks the shared grammar', () async {
      final json = {
        'apiBaseUrl': '/api',
        'keycloakUrl': 'http://localhost:8180/',
        'realm': 'mariposa',
        'clientId': 'order-tracker',
        'redirectUri': 'http://localhost:8090/',
        ...testCatalogJson(),
        'currencies': {'MXN': 2},
      };
      respond(
        http.Response(
          jsonEncode(json),
          200,
          headers: {'content-type': 'application/json; charset=utf-8'},
        ),
      );
      await expectLater(
        loader.load(appUri: appUri, cacheBuster: '1'),
        throwsA(
          isA<ConfigLoadException>()
              .having((error) => error.failure, 'failure', ConfigLoadFailure.invalidMarketCatalog)
              .having((error) => error.uri?.path, 'path', '/config.json')
              .having((error) => error.detail, 'detail', contains('COP')),
        ),
      );
    });

    test('should fail when the configuration does not arrive in time', () async {
      final slowLoader = ConfigLoader(httpClient, timeout: Duration.zero);
      when(() => httpClient.get(any())).thenAnswer((_) => Completer<http.Response>().future);
      await expectLater(
        slowLoader.load(appUri: appUri, cacheBuster: '1'),
        failsWith(ConfigLoadFailure.unavailable),
      );
    });

    test('should fail when the network is unavailable', () async {
      when(() => httpClient.get(any())).thenThrow(http.ClientException('offline'));
      await expectLater(
        loader.load(appUri: appUri, cacheBuster: '1'),
        throwsA(
          isA<ConfigLoadException>().having(
            (error) => error.uri?.path,
            'path',
            '/config.json',
          ),
        ),
      );
    });
  });
}
