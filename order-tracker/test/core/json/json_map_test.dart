import 'package:flutter_test/flutter_test.dart';
import 'package:order_tracker/core/json/json_map.dart';

void main() {
  const json = JsonMap({
    'text': 'value',
    'integer': 3,
    'decimal': 2.5,
    'date': '2026-09-18T15:42:12Z',
    'badDate': 'not-a-date',
    'object': {'key': 'nested'},
    'list': [
      {'key': 'first'},
    ],
    'nothing': null,
  });

  group('JsonMap', () {
    test('should parse a map and reject other values', () {
      expect(JsonMap.parse(<String, Object?>{'a': 1}).raw, {'a': 1});
      expect(() => JsonMap.parse('text'), throwsFormatException);
      expect(() => JsonMap.parse(null), throwsFormatException);
    });

    test('should read required and optional strings', () {
      expect(json.requireString('text'), 'value');
      expect(json.optionalString('nothing'), isNull);
      expect(json.optionalString('missing'), isNull);
      expect(() => json.requireString('missing'), throwsFormatException);
      expect(() => json.requireString('integer'), throwsFormatException);
    });

    test('should read numbers converting between int and double', () {
      expect(json.requireInt('integer'), 3);
      expect(json.requireInt('decimal'), 2);
      expect(json.requireDouble('integer'), 3.0);
      expect(json.optionalDouble('decimal'), 2.5);
      expect(json.optionalInt('missing'), isNull);
      expect(() => json.requireDouble('text'), throwsFormatException);
    });

    test('should read dates and reject invalid dates', () {
      expect(json.requireDateTime('date'), DateTime.utc(2026, 9, 18, 15, 42, 12));
      expect(json.optionalDateTime('nothing'), isNull);
      expect(() => json.requireDateTime('badDate'), throwsFormatException);
      expect(() => json.requireDateTime('missing'), throwsFormatException);
    });

    test('should read nested objects and lists', () {
      expect(json.requireObject('object').requireString('key'), 'nested');
      expect(json.optionalObject('nothing'), isNull);
      expect(json.objectList('list').single.requireString('key'), 'first');
      expect(json.objectList('missing'), isEmpty);
      expect(json.requireObjectList('list'), hasLength(1));
      expect(() => json.requireObjectList('missing'), throwsFormatException);
      expect(() => json.requireObject('text'), throwsFormatException);
    });
  });
}
