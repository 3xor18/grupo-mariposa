import 'package:http/http.dart' as http;
import 'package:order_tracker/core/http/access_token_provider.dart';
import 'package:order_tracker/core/http/http_headers.dart';
import 'package:order_tracker/core/http/http_status_codes.dart';

final class AuthenticatedHttpClient extends http.BaseClient {
  AuthenticatedHttpClient(this._inner, this._tokenProvider);

  final http.Client _inner;
  final AccessTokenProvider _tokenProvider;

  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) async {
    final token = await _tokenProvider.validAccessToken();
    if (token != null) {
      request.headers[HttpHeaderNames.authorization] = '${HttpAuthSchemes.bearer} $token';
    }
    final response = await _inner.send(request);
    if (response.statusCode == HttpStatusCodes.unauthorized) {
      _tokenProvider.onUnauthorized();
    }
    return response;
  }

  @override
  void close() => _inner.close();
}
