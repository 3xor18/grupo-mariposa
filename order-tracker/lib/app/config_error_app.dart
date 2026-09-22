import 'package:flutter/material.dart';
import 'package:order_tracker/core/l10n/app_strings.dart';
import 'package:order_tracker/core/theme/app_theme.dart';
import 'package:order_tracker/core/widgets/state_message.dart';

class ConfigErrorApp extends StatelessWidget {
  const ConfigErrorApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: AppStrings.appTitle,
      theme: AppTheme.light(),
      darkTheme: AppTheme.dark(),
      debugShowCheckedModeBanner: false,
      home: const Scaffold(
        body: StateMessage(
          icon: Icons.settings_suggest_outlined,
          title: AppStrings.configErrorTitle,
          message: AppStrings.configErrorMessage,
        ),
      ),
    );
  }
}
