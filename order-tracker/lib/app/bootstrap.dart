import 'package:flutter/widgets.dart';
import 'package:http/http.dart' as http;
import 'package:intl/date_symbol_data_local.dart';
import 'package:order_tracker/app/app_dependencies.dart';
import 'package:order_tracker/app/config_error_app.dart';
import 'package:order_tracker/app/order_tracker_app.dart';
import 'package:order_tracker/core/config/config_loader.dart';
import 'package:order_tracker/core/format/app_locale.dart';
import 'package:order_tracker/core/platform/browser_location.dart';
import 'package:order_tracker/core/time/clock.dart';

final class AppBootstrap {
  const AppBootstrap({
    required this.location,
    required this.store,
    required this.httpClient,
    this.clock = systemClock,
  });

  final BrowserLocation location;
  final KeyValueStore store;
  final http.Client httpClient;
  final Clock clock;

  Future<Widget> createApp() async {
    await initializeDateFormatting(AppLocale.languageCode);
    try {
      final config = await ConfigLoader(httpClient).load(
        appUri: location.current,
        cacheBuster: '${clock().millisecondsSinceEpoch}',
      );
      final dependencies = AppDependencies.create(
        config: config,
        location: location,
        store: store,
        httpClient: httpClient,
      );
      return OrderTrackerApp(dependencies: dependencies);
    } on ConfigLoadException {
      return const ConfigErrorApp();
    }
  }
}
