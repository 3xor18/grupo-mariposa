import 'package:flutter_test/flutter_test.dart';
import 'package:order_tracker/core/config/config_load_exception.dart';
import 'package:order_tracker/core/json/json_map.dart';
import 'package:order_tracker/core/markets/market_catalog.dart';

import '../../fixtures/market_fixtures.dart';

void main() {
  group('MarketCatalog.fromJson', () {
    test('should parse markets and currency fraction digits', () {
      expect(MarketCatalog.fromJson(JsonMap(testCatalogJson())), testCatalog);
    });

    test('should use the market code when no display name is configured', () {
      final catalog = MarketCatalog.fromJson(
        const JsonMap({
          'markets': [
            {'code': 'BR', 'currency': 'BRL', 'locale': 'pt-BR'},
          ],
          'currencies': {'BRL': 2},
        }),
      );
      expect(catalog.markets.single.name, 'BR');
    });

    test('should reject catalogs with missing or malformed members', () {
      expect(
        () => MarketCatalog.fromJson(const JsonMap({'currencies': <String, Object?>{}})),
        throwsFormatException,
      );
      expect(
        () => MarketCatalog.fromJson(
          const JsonMap({
            'markets': <Object?>[],
            'currencies': {'CLP': 'zero'},
          }),
        ),
        throwsFormatException,
      );
    });

    Map<String, Object?> catalogWith({
      List<Map<String, Object?>>? markets,
      Map<String, Object?>? currencies,
    }) {
      return {
        'markets':
            markets ??
            [
              {'code': 'MX', 'currency': 'MXN', 'locale': 'es-MX'},
            ],
        'currencies': currencies ?? {'MXN': 2},
      };
    }

    Matcher rejectedWith(String detail) {
      return throwsA(
        isA<ConfigLoadException>()
            .having((error) => error.failure, 'failure', ConfigLoadFailure.invalidMarketCatalog)
            .having((error) => error.detail, 'detail', contains(detail)),
      );
    }

    void expectRejected(Map<String, Object?> json, String detail) {
      expect(() => MarketCatalog.fromJson(JsonMap(json)), rejectedWith(detail));
    }

    test('should reject an empty catalog', () {
      expectRejected(catalogWith(markets: []), 'empty');
    });

    test('should reject codes and locales outside the shared grammar', () {
      for (final code in ['mx', 'MEX', 'M1', '']) {
        expectRejected(
          catalogWith(
            markets: [
              {'code': code, 'currency': 'MXN', 'locale': 'es-MX'},
            ],
          ),
          'Invalid market code',
        );
      }
      for (final locale in ['es', 'es_MX', 'ES-mx', 'es-MX-x']) {
        expectRejected(
          catalogWith(
            markets: [
              {'code': 'MX', 'currency': 'MXN', 'locale': locale},
            ],
          ),
          'Invalid locale',
        );
      }
      expectRejected(catalogWith(currencies: {'MXN': 2, 'mxn': 2}), 'Invalid currency code');
    });

    test('should reject fraction digits outside 0..4', () {
      expectRejected(catalogWith(currencies: {'MXN': 5}), 'out of range');
      expectRejected(catalogWith(currencies: {'MXN': -1}), 'out of range');
    });

    test('should reject duplicated markets', () {
      expectRejected(
        catalogWith(
          markets: [
            {'code': 'MX', 'currency': 'MXN', 'locale': 'es-MX'},
            {'code': 'MX', 'currency': 'MXN', 'locale': 'es-MX'},
          ],
        ),
        'Duplicated market MX',
      );
    });

    test('should reject markets whose currency has no declared digits', () {
      expectRejected(
        catalogWith(
          markets: [
            {'code': 'EC', 'currency': 'USD', 'locale': 'es-EC'},
          ],
        ),
        'USD',
      );
    });
  });

  group('lookups', () {
    test('should resolve five markets including shared and zero decimal currencies', () {
      expect(testCatalog.markets.map((market) => market.code), ['MX', 'CO', 'PE', 'CL', 'EC']);
      expect(testCatalog.fractionDigitsOf('CLP'), 0);
      expect(testCatalog.fractionDigitsOf('USD'), 2);
      expect(testCatalog.localeOf('CLP'), 'es-CL');
      expect(testCatalog.localeOf('USD'), 'es-EC');
      expect(testCatalog.nameOf('CL'), 'Chile');
    });

    test('should fall back for codes outside the catalog', () {
      expect(testCatalog.fractionDigitsOf('XYZ'), MarketCatalog.defaultFractionDigits);
      expect(testCatalog.nameOf('BR'), 'BR');
      expect(testCatalog.localeOf('XYZ'), 'es');
    });
  });
}
