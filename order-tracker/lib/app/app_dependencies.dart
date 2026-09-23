import 'package:http/http.dart' as http;
import 'package:order_tracker/core/config/app_config.dart';
import 'package:order_tracker/core/format/app_formatters.dart';
import 'package:order_tracker/core/http/api_client.dart';
import 'package:order_tracker/core/http/authenticated_http_client.dart';
import 'package:order_tracker/core/platform/browser_location.dart';
import 'package:order_tracker/core/platform/key_value_store.dart';
import 'package:order_tracker/features/auth/data/auth_repository_impl.dart';
import 'package:order_tracker/features/auth/data/oidc_client.dart';
import 'package:order_tracker/features/auth/data/oidc_endpoints.dart';
import 'package:order_tracker/features/auth/domain/auth_repository.dart';
import 'package:order_tracker/features/orders/data/datasources/orders_api.dart';
import 'package:order_tracker/features/orders/data/repositories/order_repository_impl.dart';
import 'package:order_tracker/features/orders/domain/usecases/list_orders.dart';
import 'package:order_tracker/features/orders/domain/usecases/search_order.dart';

final class AppDependencies {
  const AppDependencies({
    required this.authRepository,
    required this.searchOrder,
    required this.listOrders,
    required this.formatters,
    this._onDispose,
  });

  factory AppDependencies.create({
    required AppConfig config,
    required BrowserLocation location,
    required KeyValueStore store,
    required http.Client httpClient,
  }) {
    final endpoints = OidcEndpoints.fromConfig(config);
    final authRepository = AuthRepositoryImpl(
      oidcClient: OidcClient(httpClient, endpoints),
      endpoints: endpoints,
      redirectUri: config.redirect,
      store: store,
      location: location,
    );
    final apiClient = ApiClient(
      AuthenticatedHttpClient(httpClient, authRepository),
      config.apiBaseUri(appUri: location.current),
    );
    final orderRepository = OrderRepositoryImpl(OrdersApi(apiClient));
    return AppDependencies(
      authRepository: authRepository,
      searchOrder: SearchOrder(orderRepository),
      listOrders: ListOrders(orderRepository),
      formatters: AppFormatters(),
      onDispose: () async {
        httpClient.close();
        await authRepository.dispose();
      },
    );
  }

  final AuthRepository authRepository;
  final SearchOrder searchOrder;
  final ListOrders listOrders;
  final AppFormatters formatters;
  final Future<void> Function()? _onDispose;

  Future<void> dispose() async => _onDispose?.call();
}
