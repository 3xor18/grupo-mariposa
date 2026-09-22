import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:order_tracker/core/theme/app_theme.dart';
import 'package:order_tracker/core/theme/app_tokens.dart';
import 'package:order_tracker/core/theme/status_palette.dart';

void main() {
  test('should expose material 3 light and dark themes with status colors', () {
    final light = AppTheme.light();
    final dark = AppTheme.dark();
    expect(light.useMaterial3, isTrue);
    expect(light.colorScheme.brightness, Brightness.light);
    expect(dark.colorScheme.brightness, Brightness.dark);
    expect(light.extension<StatusPalette>(), isNotNull);
    expect(
      dark.extension<StatusPalette>()!.approved.container,
      isNot(light.extension<StatusPalette>()!.approved.container),
    );
  });

  test('should copy and interpolate the status palette', () {
    final light = StatusPalette.of(Brightness.light);
    final dark = StatusPalette.of(Brightness.dark);
    final tone = StatusTone.fromSeed(AppColors.seed, Brightness.light);
    final copy = light.copyWith(approved: tone, rejected: tone, failure: tone, neutral: tone);
    expect(copy.approved, tone);
    expect(copy.neutral, tone);
    expect(light.copyWith().rejected, light.rejected);
    expect(light.lerp(dark, 0.2), light);
    expect(light.lerp(dark, 0.8), dark);
    expect(light.lerp(null, 0.8), light);
  });
}
