import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:order_tracker/core/format/app_formatters.dart';
import 'package:order_tracker/core/theme/app_theme.dart';
import 'package:order_tracker/features/orders/domain/repositories/order_repository.dart';
import 'package:order_tracker/features/orders/domain/usecases/list_orders.dart';
import 'package:order_tracker/features/orders/domain/usecases/search_order.dart';
import '../fixtures/market_fixtures.dart';

const phoneSize = Size(390, 844);
const tabletSize = Size(1024, 768);
const desktopSize = Size(1440, 900);

Future<void> initializeTestLocale() => initializeDateFormatting('es');

extension PumpApp on WidgetTester {
  Future<void> useSize(Size size) async {
    view
      ..physicalSize = size
      ..devicePixelRatio = 1;
    addTearDown(view.reset);
  }

  Future<void> pumpOrdersApp(
    Widget child, {
    required OrderRepository repository,
    Size size = phoneSize,
  }) async {
    await useSize(size);
    await pumpWidget(
      MultiRepositoryProvider(
        providers: [
          RepositoryProvider(create: (_) => AppFormatters(testCatalog)),
          RepositoryProvider.value(value: testCatalog),
          RepositoryProvider(create: (_) => SearchOrder(repository)),
          RepositoryProvider(create: (_) => ListOrders(repository)),
        ],
        child: MaterialApp(
          theme: AppTheme.light(),
          home: Scaffold(body: child),
        ),
      ),
    );
  }
}
