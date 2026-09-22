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
  const StatusPalette({
    required this.approved,
    required this.rejected,
    required this.failure,
    required this.neutral,
  });

  factory StatusPalette.of(Brightness brightness) {
    return StatusPalette(
      approved: StatusTone.fromSeed(AppColors.approvedSeed, brightness),
      rejected: StatusTone.fromSeed(AppColors.rejectedSeed, brightness),
      failure: StatusTone.fromSeed(AppColors.failureSeed, brightness),
      neutral: StatusTone.fromSeed(AppColors.neutralSeed, brightness),
    );
  }

  final StatusTone approved;
  final StatusTone rejected;
  final StatusTone failure;
  final StatusTone neutral;

  @override
  StatusPalette copyWith({
    StatusTone? approved,
    StatusTone? rejected,
    StatusTone? failure,
    StatusTone? neutral,
  }) {
    return StatusPalette(
      approved: approved ?? this.approved,
      rejected: rejected ?? this.rejected,
      failure: failure ?? this.failure,
      neutral: neutral ?? this.neutral,
    );
  }

  @override
  StatusPalette lerp(StatusPalette? other, double t) =>
      t < _halfway || other == null ? this : other;

  static const _halfway = 0.5;
}

extension StatusPaletteContext on BuildContext {
  StatusPalette get statusPalette {
    final theme = Theme.of(this);
    return theme.extension<StatusPalette>() ?? StatusPalette.of(theme.brightness);
  }
}
