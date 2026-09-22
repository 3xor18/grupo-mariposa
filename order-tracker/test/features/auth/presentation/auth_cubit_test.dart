import 'dart:async';

import 'package:bloc_test/bloc_test.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:order_tracker/core/error/app_failure.dart';
import 'package:order_tracker/core/result/result.dart';
import 'package:order_tracker/features/auth/domain/auth_user.dart';
import 'package:order_tracker/features/auth/domain/session_restoration.dart';
import 'package:order_tracker/features/auth/presentation/auth_cubit.dart';
import 'package:order_tracker/features/auth/presentation/auth_state.dart';

import '../../../helpers/mocks.dart';

void main() {
  late MockAuthRepository repository;
  late StreamController<void> expirations;
  const user = AuthUser(name: 'Ana Analista');

  setUp(() {
    repository = MockAuthRepository();
    expirations = StreamController<void>.broadcast();
    when(() => repository.sessionExpired).thenAnswer((_) => expirations.stream);
    when(repository.login).thenAnswer((_) async {});
    when(repository.logout).thenAnswer((_) async {});
  });

  tearDown(() => expirations.close());

  AuthCubit buildCubit() => AuthCubit(repository);

  test('should start checking the session', () {
    expect(buildCubit().state, const AuthChecking());
  });

  blocTest<AuthCubit, AuthState>(
    'should authenticate when a session is restored',
    setUp: () => when(repository.restoreSession).thenAnswer((_) async => const Ok(SignedIn(user))),
    build: buildCubit,
    act: (cubit) => cubit.initialize(),
    expect: () => const [AuthAuthenticated(user)],
  );

  blocTest<AuthCubit, AuthState>(
    'should ask for login when there is no session',
    setUp: () => when(repository.restoreSession).thenAnswer((_) async => const Ok(SignedOut())),
    build: buildCubit,
    act: (cubit) => cubit.initialize(),
    expect: () => const [AuthUnauthenticated()],
  );

  blocTest<AuthCubit, AuthState>(
    'should keep checking while a silent sign in redirects to keycloak',
    setUp: () => when(
      repository.restoreSession,
    ).thenAnswer((_) async => const Ok(SigningInSilently())),
    build: buildCubit,
    act: (cubit) => cubit.initialize(),
    expect: () => const <AuthState>[],
  );

  blocTest<AuthCubit, AuthState>(
    'should fall back to the login screen when restoring throws',
    setUp: () => when(repository.restoreSession).thenThrow(const FormatException('boom')),
    build: buildCubit,
    act: (cubit) => cubit.initialize(),
    expect: () => const [
      AuthUnauthenticated(
        failure: AuthenticationFailure(AuthenticationFailureReason.unexpected),
      ),
    ],
  );

  test('should not emit after being closed while restoring', () async {
    final pending = Completer<Result<SessionRestoration>>();
    when(repository.restoreSession).thenAnswer((_) => pending.future);
    final cubit = buildCubit();
    final initialization = cubit.initialize();
    await cubit.close();
    pending.complete(const Ok(SignedIn(user)));
    await initialization;
    expect(cubit.state, const AuthChecking());
  });

  test('should not emit after being closed when restoring throws', () async {
    final pending = Completer<Result<SessionRestoration>>();
    when(repository.restoreSession).thenAnswer((_) => pending.future);
    final cubit = buildCubit();
    final initialization = cubit.initialize();
    await cubit.close();
    pending.completeError(const FormatException('late'));
    await initialization;
    expect(cubit.state, const AuthChecking());
  });

  blocTest<AuthCubit, AuthState>(
    'should show the login failure when the callback fails',
    setUp: () => when(repository.restoreSession).thenAnswer(
      (_) async => const Err(AuthenticationFailure(AuthenticationFailureReason.stateMismatch)),
    ),
    build: buildCubit,
    act: (cubit) => cubit.initialize(),
    expect: () => const [
      AuthUnauthenticated(
        failure: AuthenticationFailure(AuthenticationFailureReason.stateMismatch),
      ),
    ],
  );

  blocTest<AuthCubit, AuthState>(
    'should redirect to keycloak on login',
    build: buildCubit,
    act: (cubit) => cubit.login(),
    expect: () => const [AuthRedirecting()],
    verify: (_) => verify(repository.login).called(1),
  );

  blocTest<AuthCubit, AuthState>(
    'should redirect to keycloak on logout',
    build: buildCubit,
    seed: () => const AuthAuthenticated(user),
    act: (cubit) => cubit.logout(),
    expect: () => const [AuthRedirecting()],
    verify: (_) => verify(repository.logout).called(1),
  );

  blocTest<AuthCubit, AuthState>(
    'should return to login when the session expires',
    build: buildCubit,
    seed: () => const AuthAuthenticated(user),
    act: (_) => expirations.add(null),
    expect: () => const [
      AuthUnauthenticated(
        failure: AuthenticationFailure(AuthenticationFailureReason.sessionExpired),
      ),
    ],
  );
}
