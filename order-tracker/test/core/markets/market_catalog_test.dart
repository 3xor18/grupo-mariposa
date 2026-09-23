import 'package:flutter_test/flutter_test.dart';
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
