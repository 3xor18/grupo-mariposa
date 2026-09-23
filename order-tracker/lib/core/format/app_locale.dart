import 'package:flutter/widgets.dart';

abstract final class AppLocale {
  static const languageCode = 'es';
  static const locale = Locale(languageCode);
  static const List<Locale> supportedLocales = [locale];
}
