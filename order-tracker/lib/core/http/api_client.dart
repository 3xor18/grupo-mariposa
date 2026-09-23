import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:order_tracker/core/error/app_failure.dart';
import 'package:order_tracker/core/error/failure_mapper.dart';
import 'package:order_tracker/core/error/problem_details.dart';
import 'package:order_tracker/core/http/app_timeouts.dart';
import 'package:order_tracker/core/http/http_headers.dart';
import 'package:order_tracker/core/http/http_status_codes.dart';
import 'package:order_tracker/core/json/json_map.dart';
import 'package:order_tracker/core/result/result.dart';

final class ApiClient {
  ApiClient(this._httpClient, this._baseUri, {this._timeout = AppTimeouts.network});

  static const Map<String, String> _headers = {
    HttpHeaderNames.accept: '${HttpMediaTypes.json}, ${HttpMediaTypes.problemJson}',
  };

  final http.Client _httpClient;
  final Uri _baseUri;
  final Duration _timeout;

  Future<Result<Object?>> getJson(
    List<String> pathSegments, {
    Map<String, String> query = const {},
  }) async {
    try {
      final uri = resolve(pathSegments, query: query);
      final response = await _httpClient.get(uri, headers: _headers).timeout(_timeout);
      return _handle(response);
    } on TimeoutException {
      return const Result.err(NetworkFailure());
    } on http.ClientException {
      return const Result.err(NetworkFailure());
    }
  }

  Uri resolve(List<String> pathSegments, {Map<String, String> query = const {}}) {
    final baseSegments = _baseUri.pathSegments.where((segment) => segment.isNotEmpty);
    return _baseUri.replace(
      pathSegments: [...baseSegments, ...pathSegments],
      queryParameters: query.isEmpty ? null : query,
    );
  }

  Result<Object?> _handle(http.Response response) {
    if (HttpStatusCodes.isSuccess(response.statusCode)) {
      return _decode(response);
    }
    final failure = FailureMapper.fromResponse(
      statusCode: response.statusCode,
      problem: _problemOf(response),
      retryAfter: response.headers[HttpHeaderNames.retryAfter],
    );
    return Result.err(failure);
  }

  Result<Object?> _decode(http.Response response) {
    try {
      return Result.ok(jsonDecode(utf8.decode(response.bodyBytes)));
    } on FormatException {
      return const Result.err(UnexpectedResponseFailure());
    }
  }

  ProblemDetails? _problemOf(http.Response response) {
    final contentType = response.headers[HttpHeaderNames.contentType] ?? '';
    if (!contentType.contains(HttpMediaTypes.jsonSuffix)) {
      return null;
    }
    try {
      return ProblemDetails.fromJson(JsonMap.parse(jsonDecode(utf8.decode(response.bodyBytes))));
    } on FormatException {
      return null;
    }
  }
}
