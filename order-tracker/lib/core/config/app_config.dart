import 'package:equatable/equatable.dart';
import 'package:order_tracker/core/json/json_map.dart';
import 'package:order_tracker/core/markets/market_catalog.dart';

abstract final class _ConfigFields {
  static const apiBaseUrl = 'apiBaseUrl';
  static const keycloakUrl = 'keycloakUrl';
  static const realm = 'realm';
  static const clientId = 'clientId';
  static const redirectUri = 'redirectUri';
  static const enableSemantics = 'enableSemantics';
}

final class AppConfig extends Equatable {
  const AppConfig({
    required this.apiBaseUrl,
    required this.keycloakUrl,
    required this.realm,
    required this.clientId,
    required this.redirectUri,
    required this.catalog,
    this.enableSemantics = false,
  });

  factory AppConfig.fromJson(JsonMap json) {
    return AppConfig(
      apiBaseUrl: json.requireString(_ConfigFields.apiBaseUrl),
      keycloakUrl: json.requireString(_ConfigFields.keycloakUrl),
      realm: json.requireString(_ConfigFields.realm),
      clientId: json.requireString(_ConfigFields.clientId),
      redirectUri: json.requireString(_ConfigFields.redirectUri),
      catalog: MarketCatalog.fromJson(json),
      enableSemantics: json.optionalBool(_ConfigFields.enableSemantics) ?? false,
    );
  }

  static const _realmsSegment = 'realms';

  final String apiBaseUrl;
  final String keycloakUrl;
  final String realm;
  final String clientId;
  final String redirectUri;
  final MarketCatalog catalog;
  final bool enableSemantics;

  Uri apiBaseUri({required Uri appUri}) => appUri.resolve(apiBaseUrl);

  Uri get redirect => Uri.parse(redirectUri);

  Uri get issuerUri {
    final base = Uri.parse(keycloakUrl);
    final segments = base.pathSegments.where((segment) => segment.isNotEmpty);
    return base.replace(pathSegments: [...segments, _realmsSegment, realm]);
  }

  @override
  List<Object?> get props => [
    apiBaseUrl,
    keycloakUrl,
    realm,
    clientId,
    redirectUri,
    catalog,
    enableSemantics,
  ];
}
