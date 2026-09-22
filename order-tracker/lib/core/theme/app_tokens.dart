import 'package:flutter/material.dart';

abstract final class AppSpacing {
  static const xxs = 2.0;
  static const xs = 4.0;
  static const sm = 8.0;
  static const md = 16.0;
  static const lg = 24.0;
  static const xl = 32.0;
}

abstract final class AppBreakpoints {
  static const medium = 600.0;
  static const expanded = 840.0;
}

abstract final class AppSizes {
  static const maxContentWidth = 1200.0;
  static const maxReadableWidth = 960.0;
  static const maxFormWidth = 420.0;
  static const listPaneWidth = 400.0;
  static const emptyStateIcon = 64.0;
  static const brandIcon = 72.0;
  static const progressIndicator = 20.0;
  static const progressStroke = 2.0;
  static const cardRadius = 16.0;
  static const badgeRadius = 8.0;
  static const badgeIcon = 16.0;
  static const compactBadgeIcon = 14.0;
  static const dividerThickness = 1.0;
}

abstract final class AppColors {
  static const seed = Color(0xFF5B3FA8);
  static const approvedSeed = Color(0xFF2E7D32);
  static const rejectedSeed = Color(0xFFC62828);
  static const failureSeed = Color(0xFFEF6C00);
  static const neutralSeed = Color(0xFF607D8B);
}
