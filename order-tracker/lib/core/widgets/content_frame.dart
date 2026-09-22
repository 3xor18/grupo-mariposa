import 'package:flutter/material.dart';
import 'package:order_tracker/core/theme/app_tokens.dart';

class ContentFrame extends StatelessWidget {
  const ContentFrame({
    required this.child,
    this.maxWidth = AppSizes.maxContentWidth,
    super.key,
  });

  final Widget child;
  final double maxWidth;

  @override
  Widget build(BuildContext context) {
    return Align(
      alignment: Alignment.topCenter,
      child: ConstrainedBox(
        constraints: BoxConstraints(maxWidth: maxWidth),
        child: SizedBox.expand(child: child),
      ),
    );
  }
}
