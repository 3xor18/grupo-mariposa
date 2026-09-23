enum ConfigLoadFailure { unavailable, malformed, invalidMarketCatalog }

final class ConfigLoadException implements Exception {
  const ConfigLoadException(this.failure, {this.uri, this.detail});

  final ConfigLoadFailure failure;
  final Uri? uri;
  final String? detail;

  ConfigLoadException at(Uri location) {
    return ConfigLoadException(failure, uri: location, detail: detail);
  }

  @override
  String toString() => 'ConfigLoadException(${failure.name}, $uri, $detail)';
}
