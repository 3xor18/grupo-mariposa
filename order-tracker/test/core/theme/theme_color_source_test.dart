import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:order_tracker/core/theme/app_tokens.dart';

void main() {
  const hexRadix = 16;
  const rgbDigits = 6;
  final seedHex =
      '#${(AppColors.seed.toARGB32() & 0xFFFFFF).toRadixString(hexRadix).padLeft(rgbDigits, '0')}'
          .toUpperCase();

  test('should use the theme seed as the PWA manifest theme color', () {
    final manifest =
        jsonDecode(File('web/manifest.json').readAsStringSync()) as Map<String, Object?>;
    expect((manifest['theme_color']! as String).toUpperCase(), seedHex);
  });

  test('should use the theme seed as the browser theme color', () {
    final html = File('web/index.html').readAsStringSync();
    expect(html.toUpperCase(), contains('<META NAME="THEME-COLOR" CONTENT="$seedHex">'));
  });
}
