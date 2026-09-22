import 'package:flutter/material.dart';
import 'package:order_tracker/core/theme/app_tokens.dart';

@immutable
final class StatusTone {
  const StatusTone({required this.container, required this.onContainer});

  factory StatusTone.fromSeed(Color seed, Brightness brightness) {
    final scheme = ColorScheme.fromSeed(seedColor: seed, brightness: brightness);
    return StatusTone(container: scheme.primaryContainer, onContainer: scheme.onPrimaryContainer);
  }

  final Color container;
  final Color onContainer;
}

@immutable
final class StatusPalette extends ThemeExtension<StatusPalette> {
  const StatusPalette({required this.approved, required this.rejected, required this.failure});

  factory StatusPalette.of(Brightness brightness) {
    return StatusPalette(
      approved: StatusTone.fromSeed(AppColors.approvedSeed, brightness),
      rejected: StatusTone.fromSeed(AppColors.rejectedSeed, brightness),
      failure: StatusTone.fromSeed(AppColors.failureSeed, brightness),
    );
  }

  final StatusTone approved;
  final StatusTone rejected;
  final StatusTone failure;

  @override
  StatusPalette copyWith({StatusTone? approved, StatusTone? rejected, StatusTone? failure}) {
    return StatusPalette(
      approved: approved ?? this.approved,
      rejected: rejected ?? this.rejected,
      failure: failure ?? this.failure,
    );
  }

  @override
  StatusPalette lerp(StatusPalette? other, double t) =>
      t < _halfway || other == null ? this : other;

  static const _halfway = 0.5;
}
