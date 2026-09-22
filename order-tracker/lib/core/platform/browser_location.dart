abstract interface class BrowserLocation {
  Uri get current;

  void assign(Uri uri);

  void replace(Uri uri);
}

abstract interface class KeyValueStore {
  String? read(String key);

  void write(String key, String value);

  void remove(String key);
}
