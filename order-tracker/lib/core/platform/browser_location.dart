abstract interface class BrowserLocation {
  Uri get current;

  void assign(Uri uri);

  void replace(Uri uri);
}
