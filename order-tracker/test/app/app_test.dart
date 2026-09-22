import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:mocktail/mocktail.dart';
import 'package:order_tracker/app/app_dependencies.dart';
import 'package:order_tracker/app/bootstrap.dart';
import 'package:order_tracker/app/config_error_app.dart';
import 'package:order_tracker/app/order_tracker_app.dart';
import 'package:order_tracker/app/shell_keys.dart';
import 'package:order_tracker/core/config/app_config.dart';
import 'package:order_tracker/core/format/app_formatters.dart';
import 'package:order_tracker/core/l10n/app_strings.dart';
import 'package:order_tracker/core/result/result.dart';
import 'package:order_tracker/features/auth/data/auth_repository_impl.dart';
import 'package:order_tracker/features/auth/domain/auth_user.dart';
import 'package:order_tracker/features/auth/domain/session_restoration.dart';
import 'package:order_tracker/features/auth/presentation/auth_keys.dart';
import 'package:order_tracker/features/orders/domain/entities/orders_filter.dart';
import 'package:order_tracker/features/orders/domain/usecases/list_orders.dart';
import 'package:order_tracker/features/orders/domain/usecases/search_order.dart';
import 'package:order_tracker/features/orders/presentation/orders_keys.dart';

import '../fixtures/order_fixtures.dart';
import '../helpers/mocks.dart';
import '../helpers/pump_app.dart';

void main() {
  late MockAuthRepository authRepository;
  late MockOrderRepository orderRepository;
  late StreamController<void> expirations;

  setUpAll(() async {
    registerCommonFallbacks();
    registerFallbackValue(const OrdersFilter());
    await initializeTestLocale();
  });

  setUp(() {
    authRepository = MockAuthRepository();
    orderRepository = MockOrderRepository();
    expirations = StreamController<void>.broadcast();
    when(() => authRepository.sessionExpired).thenAnswer((_) => expirations.stream);
    when(
      () => orderRepository.list(
        filter: any(named: 'filter'),
        page: any(named: 'page'),
        size: any(named: 'size'),
      ),
    ).thenAnswer((_) async => Ok(orderPage(items: [summary(approvedOrderId)])));
  });

  tearDown(() => expirations.close());

  var disposals = 0;

  Future<void> pumpApp(WidgetTester tester, {required Size size, AuthUser? user}) async {
    final restoration = user == null ? const SignedOut() : SignedIn(user);
    when(authRepository.restoreSession).thenAnswer((_) async => Ok(restoration));
    await tester.useSize(size);
    await tester.pumpWidget(
      OrderTrackerApp(
        dependencies: AppDependencies(
          authRepository: authRepository,
          searchOrder: SearchOrder(orderRepository),
          listOrders: ListOrders(orderRepository),
          formatters: AppFormatters(),
          onDispose: () async => disposals++,
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  group('OrderTrackerApp', () {
    const user = AuthUser(name: 'Ana Analista');

    testWidgets('should ask for login without a session', (tester) async {
      await pumpApp(tester, size: phoneSize);
      expect(find.byKey(AuthKeys.loginPage), findsOneWidget);
    });

    testWidgets('should release its dependencies when removed', (tester) async {
      await pumpApp(tester, size: phoneSize);
      final before = disposals;
      await tester.pumpWidget(const SizedBox.shrink());
      expect(disposals, before + 1);
    });

    testWidgets('should load the orders list only when its tab is opened', (tester) async {
      await pumpApp(tester, size: phoneSize, user: user);
      verifyNever(
        () => orderRepository.list(
          filter: any(named: 'filter'),
          page: any(named: 'page'),
          size: any(named: 'size'),
        ),
      );
      await tester.tap(find.text(AppStrings.navOrders).last);
      await tester.pumpAndSettle();
      await tester.tap(find.text(AppStrings.search).last);
      await tester.pumpAndSettle();
      expect(find.byKey(OrdersKeys.searchField), findsOneWidget);
      verify(
        () => orderRepository.list(
          filter: any(named: 'filter'),
          page: any(named: 'page'),
          size: any(named: 'size'),
        ),
      ).called(1);
    });

    testWidgets('should use a bottom navigation bar on phones', (tester) async {
      await pumpApp(tester, size: phoneSize, user: user);
      expect(find.byKey(ShellKeys.navigationBar), findsOneWidget);
      expect(find.byKey(ShellKeys.navigationRail), findsNothing);
      expect(find.byKey(OrdersKeys.searchField), findsOneWidget);
      expect(find.text('Ana Analista'), findsOneWidget);
      await tester.tap(find.text(AppStrings.navOrders).last);
      await tester.pumpAndSettle();
      expect(find.byKey(OrdersKeys.summaryTile(approvedOrderId)), findsOneWidget);
    });

    testWidgets('should use a navigation rail on tablets and desktops', (tester) async {
      await pumpApp(tester, size: tabletSize, user: user);
      expect(find.byKey(ShellKeys.navigationRail), findsOneWidget);
      expect(find.byKey(ShellKeys.navigationBar), findsNothing);
      await tester.tap(find.text(AppStrings.navOrders).last);
      await tester.pumpAndSettle();
      expect(find.byKey(OrdersKeys.detailPlaceholder), findsOneWidget);
    });
  });

  group('AppBootstrap', () {
    late MockHttpClient httpClient;
    final location = FakeBrowserLocation(Uri.parse('http://localhost:8090/'));

    late int semanticsRequests;

    setUp(() {
      httpClient = MockHttpClient();
      semanticsRequests = 0;
    });

    AppBootstrap bootstrap() {
      return AppBootstrap(
        location: location,
        store: InMemoryKeyValueStore(),
        httpClient: httpClient,
        enableSemantics: () => semanticsRequests++,
        clock: () => DateTime.utc(2026),
      );
    }

    void respondConfig({required bool enableSemantics}) {
      when(() => httpClient.get(any())).thenAnswer(
        (_) async => http.Response(
          jsonEncode({
            'apiBaseUrl': '/api',
            'keycloakUrl': 'http://localhost:8180',
            'realm': 'mariposa',
            'clientId': 'order-tracker',
            'redirectUri': 'http://localhost:8090/',
            'enableSemantics': enableSemantics,
          }),
          200,
        ),
      );
    }

    test('should build the app and enable semantics when configured', () async {
      respondConfig(enableSemantics: true);
      final app = await bootstrap().createApp();
      expect(app, isA<OrderTrackerApp>());
      final dependencies = (app as OrderTrackerApp).dependencies;
      expect(dependencies.authRepository, isA<AuthRepositoryImpl>());
      expect(semanticsRequests, 1);
    });

    test('should keep semantics off unless configured', () async {
      respondConfig(enableSemantics: false);
      await bootstrap().createApp();
      expect(semanticsRequests, 0);
    });

    testWidgets('should explain when the runtime configuration is missing', (tester) async {
      when(() => httpClient.get(any())).thenAnswer((_) async => http.Response('', 404));
      final app = await tester.runAsync(() => bootstrap().createApp());
      expect(app, isA<ConfigErrorApp>());
      await tester.pumpWidget(app!);
      expect(find.text(AppStrings.configErrorTitle), findsOneWidget);
    });
  });

  test('should wire the dependency graph and release it on dispose', () async {
    final httpClient = MockHttpClient();
    final dependencies = AppDependencies.create(
      config: const AppConfig(
        apiBaseUrl: '/api',
        keycloakUrl: 'http://localhost:8180',
        realm: 'mariposa',
        clientId: 'order-tracker',
        redirectUri: 'http://localhost:8090/',
      ),
      location: FakeBrowserLocation(Uri.parse('http://localhost:8090/')),
      store: InMemoryKeyValueStore(),
      httpClient: httpClient,
    );
    expect(dependencies.searchOrder, isA<SearchOrder>());
    expect(dependencies.listOrders, isA<ListOrders>());
    await dependencies.dispose();
    verify(httpClient.close).called(1);
  });

  test('should tolerate disposing dependencies without resources', () async {
    final dependencies = AppDependencies(
      authRepository: MockAuthRepository(),
      searchOrder: SearchOrder(MockOrderRepository()),
      listOrders: ListOrders(MockOrderRepository()),
      formatters: AppFormatters(),
    );
    await expectLater(dependencies.dispose(), completes);
  });
}
