import 'package:flutter/material.dart';
import 'package:order_tracker/core/theme/app_tokens.dart';
import 'package:order_tracker/core/theme/status_palette.dart';

abstract final class AppTheme {
  static ThemeData light() => _build(Brightness.light);

  static ThemeData dark() => _build(Brightness.dark);

  static ThemeData _build(Brightness brightness) {
    final scheme = ColorScheme.fromSeed(seedColor: AppColors.seed, brightness: brightness);
    return ThemeData(
      colorScheme: scheme,
      visualDensity: VisualDensity.standard,
      extensions: [StatusPalette.of(brightness)],
      cardTheme: const CardThemeData(
        margin: EdgeInsets.zero,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.all(Radius.circular(AppSizes.cardRadius)),
        ),
      ),
      inputDecorationTheme: const InputDecorationTheme(border: OutlineInputBorder()),
    );
  }
}
