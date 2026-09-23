import 'package:flutter/widgets.dart';
import 'package:http/http.dart' as http;
import 'package:intl/date_symbol_data_local.dart';
import 'package:order_tracker/app/app_dependencies.dart';
import 'package:order_tracker/app/config_error_app.dart';
import 'package:order_tracker/app/order_tracker_app.dart';
import 'package:order_tracker/app/silent_sign_in_view.dart';
import 'package:order_tracker/core/config/config_loader.dart';
import 'package:order_tracker/core/format/app_locale.dart';
import 'package:order_tracker/core/platform/browser_location.dart';
import 'package:order_tracker/core/platform/key_value_store.dart';
import 'package:order_tracker/core/time/clock.dart';
import 'package:order_tracker/features/auth/presentation/auth_cubit.dart';
import 'package:order_tracker/features/auth/presentation/auth_state.dart';

final class AppBootstrap {
  const AppBootstrap({
    required this.location,
    required this.store,
    required this.httpClient,
    required this.enableSemantics,
    this.clock = systemClock,
  });

  final BrowserLocation location;
  final KeyValueStore store;
  final http.Client httpClient;
  final VoidCallback enableSemantics;
  final Clock clock;

  Future<Widget> createApp() async {
    await initializeDateFormatting(AppLocale.languageCode);
    try {
      final config = await ConfigLoader(httpClient).load(
        appUri: location.current,
        cacheBuster: '${clock().millisecondsSinceEpoch}',
      );
      if (config.enableSemantics) {
        enableSemantics();
      }
      final dependencies = AppDependencies.create(
        config: config,
        location: location,
        store: store,
        httpClient: httpClient,
      );
      final authCubit = AuthCubit(dependencies.authRepository);
      await authCubit.initialize();
      if (authCubit.state is AuthSigningInSilently) {
        return const SilentSignInView();
      }
      return OrderTrackerApp(dependencies: dependencies, authCubit: authCubit);
    } on ConfigLoadException {
      return const ConfigErrorApp();
    }
  }
}
