const _invalidField = 'Invalid or missing field';
const _invalidObject = 'Expected a JSON object';

extension type const JsonMap(Map<String, Object?> raw) {
  factory JsonMap.parse(Object? value) {
    if (value is Map<String, Object?>) {
      return JsonMap(value);
    }
    throw const FormatException(_invalidObject);
  }

  String requireString(String key) => _require(key, optionalString(key));

  String? optionalString(String key) => _optional<String>(key);

  int requireInt(String key) => _require(key, optionalInt(key));

  int? optionalInt(String key) => _optional<num>(key)?.toInt();

  double requireDouble(String key) => _require(key, optionalDouble(key));

  double? optionalDouble(String key) => _optional<num>(key)?.toDouble();

  bool? optionalBool(String key) => _optional<bool>(key);

  DateTime requireDateTime(String key) => _require(key, optionalDateTime(key));

  DateTime? optionalDateTime(String key) {
    final value = optionalString(key);
    return value == null ? null : _parseDate(key, value);
  }

  JsonMap requireObject(String key) => JsonMap.parse(raw[key]);

  JsonMap? optionalObject(String key) => raw[key] == null ? null : requireObject(key);

  List<JsonMap> objectList(String key) {
    final value = _optional<List<Object?>>(key);
    return value == null ? const [] : value.map(JsonMap.parse).toList(growable: false);
  }

  List<JsonMap> requireObjectList(String key) {
    _require(key, _optional<List<Object?>>(key));
    return objectList(key);
  }

  T? _optional<T extends Object>(String key) {
    final value = raw[key];
    if (value == null || value is T) {
      return value as T?;
    }
    throw FormatException(_invalidField, key);
  }

  T _require<T extends Object>(String key, T? value) {
    if (value == null) {
      throw FormatException(_invalidField, key);
    }
    return value;
  }

  DateTime _parseDate(String key, String value) {
    final parsed = DateTime.tryParse(value);
    return _require(key, parsed);
  }
}
