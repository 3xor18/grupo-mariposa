import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:order_tracker/core/config/app_config.dart';
import 'package:order_tracker/core/http/http_status_codes.dart';
import 'package:order_tracker/core/json/json_map.dart';

final class ConfigLoadException implements Exception {
  const ConfigLoadException(this.uri);

  final Uri uri;
}

final class ConfigLoader {
  const ConfigLoader(this._httpClient);

  static const fileName = 'config.json';
  static const _cacheBusterParameter = 'v';

  final http.Client _httpClient;

  Future<AppConfig> load({required Uri appUri, required String cacheBuster}) async {
    final uri = appUri
        .resolve(fileName)
        .replace(queryParameters: {_cacheBusterParameter: cacheBuster});
    try {
      final response = await _httpClient.get(uri);
      if (!HttpStatusCodes.isSuccess(response.statusCode)) {
        throw ConfigLoadException(uri);
      }
      return AppConfig.fromJson(JsonMap.parse(jsonDecode(utf8.decode(response.bodyBytes))));
    } on FormatException {
      throw ConfigLoadException(uri);
    } on http.ClientException {
      throw ConfigLoadException(uri);
    }
  }
}
