import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:order_tracker/core/l10n/app_strings.dart';
import 'package:order_tracker/core/theme/app_tokens.dart';
import 'package:order_tracker/features/auth/domain/auth_user.dart';
import 'package:order_tracker/features/auth/presentation/auth_cubit.dart';
import 'package:order_tracker/features/auth/presentation/auth_keys.dart';

class UserMenu extends StatelessWidget {
  const UserMenu({required this.user, super.key});

  final AuthUser user;

  @override
  Widget build(BuildContext context) {
    final name = user.displayName ?? AppStrings.unknownUser;
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Semantics(
          label: AppStrings.signedInAs(name),
          excludeSemantics: true,
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: AppSizes.userNameWidth),
            child: Text(
              name,
              key: AuthKeys.userName,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.titleSmall,
            ),
          ),
        ),
        IconButton(
          key: AuthKeys.logoutButton,
          tooltip: AppStrings.logout,
          onPressed: () => context.read<AuthCubit>().logout(),
          icon: const Icon(Icons.logout),
        ),
        const SizedBox(width: AppSpacing.sm),
      ],
    );
  }
}
