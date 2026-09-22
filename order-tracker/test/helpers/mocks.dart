import 'package:http/http.dart' as http;
import 'package:mocktail/mocktail.dart';
import 'package:order_tracker/core/http/access_token_provider.dart';
import 'package:order_tracker/core/platform/browser_location.dart';
import 'package:order_tracker/features/auth/domain/auth_repository.dart';
import 'package:order_tracker/features/orders/domain/repositories/order_repository.dart';

class MockHttpClient extends Mock implements http.Client {}

class MockAccessTokenProvider extends Mock implements AccessTokenProvider {}

class MockOrderRepository extends Mock implements OrderRepository {}

void registerCommonFallbacks() {
  registerFallbackValue(Uri());
  registerFallbackValue(http.Request('GET', Uri()));
}

class MockAuthRepository extends Mock implements AuthRepository {}

class InMemoryKeyValueStore implements KeyValueStore {
  final Map<String, String> values = {};

  @override
  String? read(String key) => values[key];

  @override
  void write(String key, String value) => values[key] = value;

  @override
  void remove(String key) => values.remove(key);
}

class FakeBrowserLocation implements BrowserLocation {
  FakeBrowserLocation(this.current);

  @override
  Uri current;

  final List<Uri> assigned = [];

  @override
  void assign(Uri uri) => assigned.add(uri);

  @override
  void replace(Uri uri) => current = uri;
}
