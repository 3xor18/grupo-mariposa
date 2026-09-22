enum OrderIdError { empty, tooLong }

abstract final class OrderId {
  static const maxLength = 64;

  static String normalize(String raw) => raw.trim().toUpperCase();

  static OrderIdError? validate(String raw) {
    final value = normalize(raw);
    if (value.isEmpty) {
      return OrderIdError.empty;
    }
    return value.length > maxLength ? OrderIdError.tooLong : null;
  }
}
