import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:order_tracker/core/error/app_failure.dart';
import 'package:order_tracker/core/l10n/app_strings.dart';
import 'package:order_tracker/core/result/result.dart';
import 'package:order_tracker/core/theme/app_theme.dart';
import 'package:order_tracker/features/auth/domain/auth_user.dart';
import 'package:order_tracker/features/auth/presentation/auth_cubit.dart';
import 'package:order_tracker/features/auth/presentation/auth_gate.dart';
import 'package:order_tracker/features/auth/presentation/auth_keys.dart';
import 'package:order_tracker/features/auth/presentation/user_menu.dart';

import '../../../helpers/mocks.dart';

void main() {
  late MockAuthRepository repository;
  late AuthCubit cubit;
  late StreamController<void> expirations;
  const user = AuthUser(name: 'Ana Analista', username: 'analyst');

  setUp(() {
    repository = MockAuthRepository();
    expirations = StreamController<void>.broadcast();
    when(() => repository.sessionExpired).thenAnswer((_) => expirations.stream);
    when(repository.login).thenAnswer((_) async {});
    when(repository.logout).thenAnswer((_) async {});
    cubit = AuthCubit(repository);
  });

  tearDown(() async {
    await cubit.close();
    await expirations.close();
  });

  Future<void> pumpGate(WidgetTester tester) {
    return tester.pumpWidget(
      BlocProvider.value(
        value: cubit,
        child: MaterialApp(
          theme: AppTheme.light(),
          home: AuthGate(
            authenticated: (context, user) => Scaffold(
              appBar: AppBar(actions: [UserMenu(user: user)]),
            ),
          ),
        ),
      ),
    );
  }

  testWidgets('should show a progress indicator while the session is checked', (tester) async {
    await pumpGate(tester);
    expect(find.byKey(AuthKeys.checkingView), findsOneWidget);
    expect(find.text(AppStrings.checkingSession), findsOneWidget);
  });

  testWidgets('should show the login screen and start the login flow', (tester) async {
    when(repository.restoreSession).thenAnswer((_) async => const Ok(null));
    await pumpGate(tester);
    await cubit.initialize();
    await tester.pump();
    expect(find.byKey(AuthKeys.loginPage), findsOneWidget);
    expect(find.text(AppStrings.loginButton), findsOneWidget);
    expect(find.byKey(AuthKeys.loginError), findsNothing);
    await tester.tap(find.byKey(AuthKeys.loginButton));
    await tester.pump();
    verify(repository.login).called(1);
    final button = tester.widget<ButtonStyleButton>(find.byKey(AuthKeys.loginButton));
    expect(button.onPressed, isNull);
  });

  testWidgets('should explain why the login failed', (tester) async {
    when(repository.restoreSession).thenAnswer(
      (_) async => const Err(AuthenticationFailure(AuthenticationFailureReason.tokenExchange)),
    );
    await pumpGate(tester);
    await cubit.initialize();
    await tester.pump();
    expect(find.text(AppStrings.loginFailedError), findsOneWidget);
  });

  testWidgets('should show the user name and log out', (tester) async {
    when(repository.restoreSession).thenAnswer((_) async => const Ok(user));
    await pumpGate(tester);
    await cubit.initialize();
    await tester.pump();
    expect(find.text('Ana Analista'), findsOneWidget);
    expect(find.bySemanticsLabel(AppStrings.signedInAs('Ana Analista')), findsOneWidget);
    await tester.tap(find.byKey(AuthKeys.logoutButton));
    await tester.pump();
    verify(repository.logout).called(1);
  });

  testWidgets('should show a generic name when the token has no profile', (tester) async {
    when(repository.restoreSession).thenAnswer((_) async => const Ok(AuthUser()));
    await pumpGate(tester);
    await cubit.initialize();
    await tester.pump();
    expect(find.text(AppStrings.unknownUser), findsOneWidget);
  });

  testWidgets('should show that the session expired', (tester) async {
    when(repository.restoreSession).thenAnswer((_) async => const Ok(user));
    await pumpGate(tester);
    await cubit.initialize();
    await tester.runAsync(() async {
      expirations.add(null);
      await pumpEventQueue();
    });
    await tester.pump();
    expect(find.text(AppStrings.unauthorizedError), findsOneWidget);
  });
}
