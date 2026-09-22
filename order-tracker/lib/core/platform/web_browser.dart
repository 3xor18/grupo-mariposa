import 'package:order_tracker/core/platform/browser_location.dart';
import 'package:web/web.dart' as web;

final class WebBrowserLocation implements BrowserLocation {
  const WebBrowserLocation();

  static const _unusedHistoryTitle = '';

  @override
  Uri get current => Uri.parse(web.window.location.href);

  @override
  void assign(Uri uri) => web.window.location.assign(uri.toString());

  @override
  void replace(Uri uri) => web.window.history.replaceState(null, _unusedHistoryTitle, '$uri');
}

final class WebSessionStore implements KeyValueStore {
  const WebSessionStore();

  @override
  String? read(String key) => web.window.sessionStorage.getItem(key);

  @override
  void write(String key, String value) => web.window.sessionStorage.setItem(key, value);

  @override
  void remove(String key) => web.window.sessionStorage.removeItem(key);
}
