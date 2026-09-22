import 'package:flutter/material.dart';
import 'package:order_tracker/core/l10n/app_strings.dart';
import 'package:order_tracker/core/theme/status_palette.dart';
import 'package:order_tracker/features/orders/domain/entities/market.dart';
import 'package:order_tracker/features/orders/domain/entities/order_status.dart';

extension OrderStatusPresentation on OrderStatus {
  static const List<OrderStatus> filterable = [
    OrderStatus.approved,
    OrderStatus.rejected,
    OrderStatus.technicalFailure,
  ];

  String get label => switch (this) {
    OrderStatus.approved => AppStrings.statusApproved,
    OrderStatus.rejected => AppStrings.statusRejected,
    OrderStatus.technicalFailure => AppStrings.statusTechnicalFailure,
    OrderStatus.unknown => AppStrings.statusUnknown,
  };

  IconData get icon => switch (this) {
    OrderStatus.approved => Icons.check_circle_outline,
    OrderStatus.rejected => Icons.block,
    OrderStatus.technicalFailure => Icons.warning_amber_outlined,
    OrderStatus.unknown => Icons.help_outline,
  };

  StatusTone toneIn(StatusPalette palette) => switch (this) {
    OrderStatus.approved => palette.approved,
    OrderStatus.rejected => palette.rejected,
    OrderStatus.technicalFailure => palette.failure,
    OrderStatus.unknown => palette.neutral,
  };
}

extension MarketPresentation on Market {
  String get label => switch (this) {
    Market.mx => AppStrings.marketMx,
    Market.co => AppStrings.marketCo,
    Market.pe => AppStrings.marketPe,
  };
}
