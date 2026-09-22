import 'package:flutter/material.dart';
import 'package:order_tracker/core/theme/app_tokens.dart';

class SilentSignInView extends StatelessWidget {
  const SilentSignInView({super.key});

  @override
  Widget build(BuildContext context) {
    return const ColoredBox(
      color: AppColors.splashBackground,
      child: Center(
        child: SizedBox.square(
          dimension: AppSizes.brandIcon,
          child: CircularProgressIndicator(color: AppColors.seed),
        ),
      ),
    );
  }
}
