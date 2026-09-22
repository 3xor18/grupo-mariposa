import 'package:http/http.dart' as http;
import 'package:mocktail/mocktail.dart';
import 'package:order_tracker/core/http/access_token_provider.dart';
import 'package:order_tracker/features/orders/domain/repositories/order_repository.dart';

class MockHttpClient extends Mock implements http.Client {}

class MockAccessTokenProvider extends Mock implements AccessTokenProvider {}

class MockOrderRepository extends Mock implements OrderRepository {}

void registerCommonFallbacks() {
  registerFallbackValue(Uri());
  registerFallbackValue(http.Request('GET', Uri()));
}
