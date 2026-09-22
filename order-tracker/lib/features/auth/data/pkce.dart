import 'dart:convert';
import 'dart:math';

import 'package:crypto/crypto.dart';
import 'package:equatable/equatable.dart';

final class PkcePair extends Equatable {
  const PkcePair({required this.verifier, required this.challenge});

  final String verifier;
  final String challenge;

  @override
  List<Object?> get props => [verifier, challenge];
}

final class PkceGenerator {
  PkceGenerator({Random? random}) : _random = random ?? Random.secure();

  static const challengeMethod = 'S256';
  static const verifierByteLength = 32;
  static const stateByteLength = 16;
  static const _byteValues = 256;
  static const _padding = '=';

  final Random _random;

  PkcePair generate() {
    final verifier = randomToken(verifierByteLength);
    return PkcePair(verifier: verifier, challenge: challengeFor(verifier));
  }

  String randomToken(int byteLength) {
    final bytes = List<int>.generate(byteLength, (_) => _random.nextInt(_byteValues));
    return _base64UrlWithoutPadding(bytes);
  }

  static String challengeFor(String verifier) {
    return _base64UrlWithoutPadding(sha256.convert(ascii.encode(verifier)).bytes);
  }

  static String _base64UrlWithoutPadding(List<int> bytes) {
    return base64Url.encode(bytes).replaceAll(_padding, '');
  }
}
