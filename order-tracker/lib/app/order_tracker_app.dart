import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:order_tracker/app/app_dependencies.dart';
import 'package:order_tracker/app/home_shell.dart';
import 'package:order_tracker/core/format/app_locale.dart';
import 'package:order_tracker/core/l10n/app_strings.dart';
import 'package:order_tracker/core/theme/app_theme.dart';
import 'package:order_tracker/features/auth/presentation/auth_cubit.dart';
import 'package:order_tracker/features/auth/presentation/auth_gate.dart';

class OrderTrackerApp extends StatefulWidget {
  const OrderTrackerApp({required this.dependencies, required this.authCubit, super.key});

  final AppDependencies dependencies;
  final AuthCubit authCubit;

  @override
  State<OrderTrackerApp> createState() => _OrderTrackerAppState();
}

class _OrderTrackerAppState extends State<OrderTrackerApp> {
  AppDependencies get dependencies => widget.dependencies;

  @override
  void dispose() {
    unawaited(widget.authCubit.close());
    unawaited(dependencies.dispose());
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return MultiRepositoryProvider(
      providers: [
        RepositoryProvider.value(value: dependencies.formatters),
        RepositoryProvider.value(value: dependencies.catalog),
        RepositoryProvider.value(value: dependencies.searchOrder),
        RepositoryProvider.value(value: dependencies.listOrders),
      ],
      child: BlocProvider.value(
        value: widget.authCubit,
        child: MaterialApp(
          title: AppStrings.appTitle,
          theme: AppTheme.light(),
          darkTheme: AppTheme.dark(),
          locale: AppLocale.locale,
          supportedLocales: AppLocale.supportedLocales,
          localizationsDelegates: GlobalMaterialLocalizations.delegates,
          debugShowCheckedModeBanner: false,
          home: AuthGate(authenticated: (context, user) => HomeShell(user: user)),
        ),
      ),
    );
  }
}
